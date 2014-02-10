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

import java.text.SimpleDateFormat
import net.liftweb.common.{Box, Full, Failure, Loggable}
import scala.collection.JavaConverters._

import com.tesobe.model._
import com.tesobe.util.HBCIConnector
import org.kapott.hbci.manager.HBCIUtils

object HBCITransactionFetcher extends Loggable{


  def getTransactions(account: AccountConfig): Seq[OBPTransaction] = {
    val bankingData = HBCIConnector.getBankingData(account.bank_national_identifier, account.account_number, account.pin)
    bankingData match {
      case Full(bd) => {

        def replaceIfEmpty(valueTotest: String, replacementValue: String): String =
          if (valueTotest.isEmpty)
            replacementValue
          else
            valueTotest

        val myBankBIC = replaceIfEmpty(bd.account.bank.bic, HBCIUtils.getBICForBLZ(account.bank_national_identifier))
        val myBankName = replaceIfEmpty(bd.account.bank.name, HBCIUtils.getNameForBLZ(account.bank_national_identifier))

        val myBank = OBPBank(
          bic = myBankBIC,
          national_identifier = account.bank_national_identifier,
          name = myBankName
        )


        val myAccount = OBPAccount(
          holder = bd.account.holder,
          number = account.account_number,
          iban = bd.account.iban,
          kind = bd.account.kind,
          bank = myBank
        )

        //iterate over the UMLS lines to create the OBP transactions
        bd.umlsLines.map{
          l => {

            val otherAcc = Option(l.other)
            val otherBankBLZ = otherAcc.flatMap {account => Option(account.blz)}
            val otherBankName = otherBankBLZ match {
              case Some(result) => HBCIUtils.getNameForBLZ(otherBankBLZ.getOrElse(""))
              case None => ""
            }

            val otherBank = OBPBank(
              bic = otherAcc.flatMap {account => Option(account.bic)}.getOrElse(""),
              national_identifier = otherBankBLZ.getOrElse(""),
              name = otherBankName
            )

            val otherAccount = OBPAccount(
              holder = otherAcc.flatMap {account => Option(account.name)}.getOrElse("").trim,
              number = otherAcc.flatMap {account => Option(account.number)}.getOrElse(""),
              iban = otherAcc.flatMap {account => Option(account.iban)}.getOrElse(""),
              kind = otherAcc.flatMap {account => Option(account.`type`)}.getOrElse(""),
              bank = otherBank
            )

            val country = otherAcc.map(acc => acc.name).getOrElse("")
            val details = OBPDetails(
              kind = Option(l.text).getOrElse(""),
              posted = OBPDate(l.bdate),
              completed = OBPDate(l.bdate),
              new_balance = OBPAmount(l.saldo.value.getCurr, l.saldo.value.getDoubleValue.toString),
              value = OBPAmount(l.value.getCurr, l.value.getDoubleValue.toString),
              label = l.usage.asScala.mkString ("/"),
              other_data = Option(l.additional).getOrElse("")
            )

            OBPTransaction(
              this_account = myAccount,
              other_account = otherAccount,
              details = details
            )
          }
        }
      }
      case Failure(msg, exception, _) => {
        logger.warn("could not fetch hbci transactions for account " + account.account_number +" at " + account.bank_national_identifier)
        exception.map{e =>
          logger.warn(e.toString)
        }
        Nil
      }
      case _ => {
        logger.warn("could not fetch hbci transactions for account " + account.account_number +" at " + account.bank_national_identifier)
        //TODO: store in DB for which banks HBCI worked and not
        Nil
      }
    }
  }
}