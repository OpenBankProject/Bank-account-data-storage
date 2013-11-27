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
  Simon Redfern : simon AT tesobe DOT com
  Stefan Bethge : stefan AT tesobe DOT com
  Everett Sochowski : everett AT tesobe DOT com
  Ayoub Benali: ayoub AT tesobe DOT com

 */
package com.tesobe.lib

import java.text.SimpleDateFormat
import net.liftweb.common.{Box, Full, Failure}
import scala.collection.JavaConverters._

import com.tesobe.model._
import com.tesobe.util.HBCIConnector
import org.kapott.hbci.manager.HBCIUtils

object HBCITransactionFetcher {

  def getTransactions(account: AccountConfig): Seq[OBPTransaction] = {
    val umsLines = HBCIConnector.getUmsLines(account.bank_national_identifier, account.account_number, account.pin)
    umsLines match {
      case Full(lines) => lines.map{
        l => {

          val myBank = OBPBank(
            bic = "",
            national_identifier = account.bank_national_identifier,
            name =  HBCIUtils.getNameForBLZ(account.bank_national_identifier))

          val myAccount = OBPAccount(
            number = account.account_number,
            iban = "",
            kind = "",
            bank = myBank)

          val other = Option(l.other)
          val blz_option  = other.flatMap {b => Option(b.blz)}

          val otherBank = OBPBank(
            bic = other.flatMap {b => Option(b.bic)}.getOrElse(""),
            national_identifier = blz_option.getOrElse(""),
            name = blz_option match {
              case Some(result) => HBCIUtils.getNameForBLZ(blz_option.getOrElse(""))
              case None         => ""
            })

          val otherAccount = OBPAccount(
            number = other.flatMap {b => Option(b.number)}.getOrElse(""),
            iban = other.flatMap {b => Option(b.iban)}.getOrElse(""),
            kind = other.flatMap {b => Option(b.`type`)}.getOrElse(""),
            bank = otherBank)

          val formatter = new SimpleDateFormat("yyyy-MM-dd'T'HH:mm:ss.SSSZ")

          val details = OBPDetails(
            trans_type = "",
            posted = OBPDate(formatter.format(l.bdate)),
            completed = OBPDate(formatter.format(l.bdate)),
            new_balance = OBPAmount(l.saldo.toString(),""),
            value = OBPAmount(l.value.toString(),""),
            label = l.usage.asScala.mkString ("/"),
            other_data = l.additional)

          OBPTransaction(
            this_account = myAccount,
            other_account = otherAccount,
            details = details)
        }
      }
      case _ => Nil
    }
  }
}