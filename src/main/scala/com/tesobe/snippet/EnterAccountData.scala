/**
Open Bank Project - API
Copyright (C) 2011, 2014, TESOBE / Music Pictures Ltd

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

 */
package com.tesobe.snippet

import com.tesobe.lib.PgpEncryption
import com.tesobe.model.AddBankAccountCredentials
import com.tesobe.messageQueue.BankAccountAMQPListener.saveBankAccount
import net.liftweb.util.Helpers.randomString
import net.liftweb.util.Props
class EnterAccountData{
  import net.liftweb.http.js.{JsCmd, JsCmds}
  import JsCmds._
  import net.liftweb.http.{RequestVar, S}

  object bankId extends RequestVar("")
  object userId extends RequestVar("")
  object accountNumber extends RequestVar("")
  object accountPin extends RequestVar("")
  object bankName extends RequestVar("")
  //object accountOwnerId extends RequestVar("")
  //object accountOwnerProvider extends RequestVar("")



  private def validate(): Seq[(String, String)] = {
    var errors: Seq[(String, String)] = Nil
    if(bankId.get.isEmpty){
      errors = errors :+ (("bankError", "Bank ID field is empty"))
    }
    if(accountNumber.get.isEmpty){
      errors = errors :+ (("accountNumberError", "Account number field is empty"))
    }
    if(accountPin.get.isEmpty){
      errors = errors :+ (("accountPinError", "Account Pin field is empty"))
    }

    errors
  }

  // taken from user-login
  private def processInputs(): JsCmd = {
    // validate inputs
    val errors = validate()
    if(errors.isEmpty){
      val id = randomString(8)
      val publicKey = Props.get("publicKeyPath").getOrElse("")
      //TODO: wrap the encryption with a tryo to Handel exception
      val encryptedPin =
        PgpEncryption.encryptToString(accountPin.is, publicKey)
      //Assume None for now
      // val accountOwnerId = u.idGivenByProvider
      val accountOwnerProvider = Props.get("api.provider","http://127.0.0.1:8080")
      val accountOwnerId ="sebastian@tesobe.com"



      val message =
        AddBankAccountCredentials(
          id,
          accountNumber,
          Some(userId),
          bankId,
          bankName,
          encryptedPin,
          accountOwnerId,
          accountOwnerProvider
        )
      saveBankAccount(message)
    }
    else{
      errors.foreach{
        e => S.error(e._1, e._2)
      }
      Noop
    }
  }

  def render = {
    import net.liftweb.http.SHtml
    import net.liftweb.util.Helpers._

    "form [action]" #> {S.uri}&
    "#bankId" #> SHtml.textElem(bankId,("placeholder","123456789")) &
    "#userId" #> SHtml.textElem(userId,("placeholder","optional")) &
    "#accountNumber" #> SHtml.textElem(accountNumber,("placeholder","123456789")) &
    "#accountPin" #> SHtml.passwordElem(accountPin,("placeholder","***********")) &
    "#processSubmit" #> SHtml.hidden(processInputs)
  }
}