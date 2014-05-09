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

class BankConnectionTest{
  import net.liftweb.http.RequestVar
  import net.liftweb.http.js.{JsCmd, JsCmds}
  import JsCmds._
  import net.liftweb.http.S

  object bankId extends RequestVar("")
  object accountNumber extends RequestVar("")
  object accountPin extends RequestVar("")


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
  private def processInputs(): JsCmd = {
    // validate inputs
    val errors = validate()
    if(errors.isEmpty){
      import net.liftweb.common.Full
      import com.tesobe.lib.HBCIConnector

      val result = HBCIConnector.getBankingData(bankId.get, accountNumber.get, accountPin.get)

      val html =
        result match {
          case Full(bd) => {
            <div>{s"Success. Fetched ${bd.umlsLines.size} transactions"}</div>
          }
          case _ => {
            <div>{"Failed to get transactions"}</div>
          }
        }
      SetHtml("result", html)
    }
    else{
      errors.foreach{
        e => S.error(e._1, e._2)
      }
      Noop
    }
  }

  def render = {
    import net.liftweb.http.{SHtml,RequestVar}
    import net.liftweb.util.Helpers._

    "form [action]" #> {S.uri}&
    "#bankId" #> SHtml.textElem(bankId,("placeholder","123456789")) &
    "#accountNumber" #> SHtml.textElem(accountNumber,("placeholder","123456789")) &
    "#accountPin" #> SHtml.passwordElem(accountPin,("placeholder","***********")) &
    "#processSubmit" #> SHtml.hidden(processInputs)
  }
}