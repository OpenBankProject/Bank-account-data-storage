/**
Open Bank Project - API
Copyright (C) 2011, 2013, TESOBE / Music Pictures Ltd

This program is free software: you can redistribute it and/or modify
it under the terms of the GNU Affero General Public License as published by
the Free Software Foundation, either version 3 of the License, or
(at your option) any later version.

This program is distributed in the hope that it will be useful,
but WITHOUT ANY WARRANTY; without even the implied warranty of
MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
GNU Affero General Public License for more details.

You should have received a copy of the GNU Affero General Public License
along with this program.  If not, see <http://www.gnu.org/licenses/>.

Email: contact@tesobe.com
TESOBE / Music Pictures Ltd
Osloerstrasse 16/17
Berlin 13359, Germany

  This product includes software developed at
  TESOBE (http://www.tesobe.com/)
  by
  Ayoub Benali: ayoub AT tesobe DOT com
  Nina Gänsdorfer: nina AT tesobe DOT com

 */
package com.tesobe.lib

import java.io.File
import java.util.Properties
import net.liftweb.common.{Box, Full, Failure, Loggable}
import net.liftweb.util.Helpers.{tryo, randomString}
import scala.collection.immutable.List
import scala.collection.JavaConverters._
import scala.collection.mutable.Map
import net.liftweb.util.Props

import org.kapott.hbci.callback.HBCICallback
import org.kapott.hbci.callback.HBCICallbackConsole
import org.kapott.hbci.GV.HBCIJob
import org.kapott.hbci.GV_Result.GVRKUms
import org.kapott.hbci.GV_Result.GVRKUms.UmsLine
import org.kapott.hbci.manager.HBCIHandler
import org.kapott.hbci.manager.HBCIUtils
import org.kapott.hbci.passport.AbstractHBCIPassport
import org.kapott.hbci.passport.HBCIPassport
import org.kapott.hbci.status.HBCIExecStatus
import com.tesobe.model.{OBPAccount,OBPBank}


case class BankingData(
  val account: OBPAccount, //the account from where the HBCI request is done.
  val umlsLines : List[UmsLine] //the transactions of the account
)

object HBCIConnector extends Loggable {
  def getBankingData(blz: String, accountNumber: String, pin: String): Box[BankingData] = {
    val passphrase = randomString(5)

    lazy val settings: Map[Int,String] = Map(
      HBCICallback.NEED_COUNTRY -> "DE",
      HBCICallback.NEED_BLZ -> blz,
      HBCICallback.NEED_CUSTOMERID -> accountNumber,
      HBCICallback.NEED_FILTER -> "Base64",
      // The host will be added later:
      // We need the getPinTanURLForBLZ function of HBCIUtils, but we have to wait until HBCIUtils is initiated.
      HBCICallback.NEED_PT_PIN -> pin,
      HBCICallback.NEED_PASSPHRASE_LOAD -> passphrase,
      HBCICallback.NEED_PASSPHRASE_SAVE -> passphrase,
      HBCICallback.NEED_PORT -> "443",
      HBCICallback.NEED_USERID -> accountNumber,
      HBCICallback.NEED_CONNECTION -> "",
      HBCICallback.CLOSE_CONNECTION -> "",
      HBCICallback.NEED_PT_SECMECH -> "999"
      )

    // HBCICallbackConsole requires interaction with the console. We override callback to read the data from the settings map.
    object callback extends HBCICallbackConsole{
      override def callback(passport: org.kapott.hbci.passport.HBCIPassport, reason: Int, msg: String, datatype: Int, retData: StringBuffer){
        settings.get(reason) match{
          case Some(value) => retData.replace(0,retData.length(),value)
          case _ => super.callback(passport, reason, msg, datatype, retData)
        }
      }
    }

    // This method has to be called before every other HBCIUtils-method.
    HBCIUtils.init(null, callback)

    // According to the documentation of hbci4java, there should not be an https:// before the UTL for PinTan.
    val hbciURL =  HBCIUtils.getPinTanURLForBLZ(blz).stripPrefix("https://")

    def bankingData: Box[BankingData] =  tryo{

      // Create a random file for storing bank account credentials necessary for HBCI.
      val filepath = Props.get("pinFilesDirectory").getOrElse("") + "passport_pintan_"+randomString(5)+".dat"

      //the maximum level of logging
      val loglevel = "5"

      //HBCI Kernel settings
      HBCIUtils.setParam("client.connection.localPort",null);
      HBCIUtils.setParam("comm.standard.socks.server",null);
      HBCIUtils.setParam("log.loglevel.default", loglevel);
      HBCIUtils.setParam("kernel.rewriter",HBCIUtils.getParam("kernel.rewriter"));

      HBCIUtils.setParam("client.passport.default","PinTan");
      HBCIUtils.setParam("client.passport.PinTan.filename", filepath);
      HBCIUtils.setParam("client.passport.PinTan.checkcert","0");
      HBCIUtils.setParam("client.passport.PinTan.certfile",null);
      HBCIUtils.setParam("client.passport.PinTan.proxy",null);
      HBCIUtils.setParam("client.passport.PinTan.proxyuser",null);
      HBCIUtils.setParam("client.passport.PinTan.proxypass",null);
      HBCIUtils.setParam("client.passport.PinTan.init","1");

      // Get HBCI-version from passport or the BLZ-file, the default for PinTan is "plus".
      val passport= AbstractHBCIPassport.getInstance()
      val passportHBCIVersion: String ={
        val hbciversion = passport.getHBCIVersion
        if (hbciversion.isEmpty){
          val version = HBCIUtils.getPinTanVersionForBLZ(blz)
          if (version.isEmpty)
            "plus"
          else
            version
        }
        else
          hbciversion
      }

      HBCIUtils.setParam("client.passport.hbciversion.default", passportHBCIVersion);

      HBCIUtils.setParam("action.resetBPD","1");
      HBCIUtils.setParam("action.resetUPD","1");

      if (HBCIUtils.getParam("action.resetBPD").equals("1")) {
        passport.clearBPD();
      }
      if (HBCIUtils.getParam("action.resetUPD").equals("1")) {
        passport.clearUPD();
      }


      val hbciHandle = new HBCIHandler(HBCIUtils.getParam("client.passport.hbciversion.default"),passport);

      // Set which job should be executed, so in our case: get all transactions of a certain account.
      val job: HBCIJob =  hbciHandle.newJob("KUmsAll");
      job.setParam("my.number", accountNumber);
      job.setParam("my.blz", blz);
      job.addToQueue();

      val ret: HBCIExecStatus = hbciHandle.execute();
      val result: List[UmsLine] = job.getJobResult() match {
        case x: GVRKUms => x.getFlatData().asScala.toList
        case _ => Nil
      }

      val thisAccount = passport.getAccount(accountNumber)
      def fullStringOrEmpty(s: String): String =
        if(s == null)
          ""
        else
          s

      val myBank = OBPBank(
        bic = fullStringOrEmpty(thisAccount.bic),
        national_identifier = fullStringOrEmpty(thisAccount.blz),
        name = fullStringOrEmpty(passport.getInstName)
      )
      val myAccount = OBPAccount(
        holder = fullStringOrEmpty(thisAccount.name) + fullStringOrEmpty(thisAccount.name2),
        number = fullStringOrEmpty(thisAccount.number),
        iban = fullStringOrEmpty(thisAccount.iban),
        kind  = fullStringOrEmpty(thisAccount.`type`),
        bank = myBank
      )
      val transactions = result
      val bd = BankingData(
        myAccount,
        transactions
      )

      if (hbciHandle!=null) {
        hbciHandle.close();
      } else if (passport!=null) {
        passport.close();
      }
      // Delete file with credentials.
      tryo {
        new File(filepath)
        }.map {
          f => {
            if (!f.delete)
              logger.error("could not delete file "+filepath)
          }
        }
      bd
    }

    if (hbciURL.isEmpty)
      Failure ("no HBCI URL available for BLZ "+blz)
    else{
      settings += ((HBCICallback.NEED_HOST, hbciURL))
      bankingData
    }
  }
}

