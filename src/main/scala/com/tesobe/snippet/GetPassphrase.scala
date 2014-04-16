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
package com.tesobe.snippet

import net.liftweb.http._
import net.liftweb.util.Helpers._
import net.liftweb.common.Loggable
import com.tesobe.messageQueue.TransactionAccountUpdateAMQPListener

// Get passphrase from index.html to decrypt encrypted data.
class GetPassphrase extends Loggable{
  def render = {
    if (TransactionAccountUpdateAMQPListener.decyrptionPassphrase == "") {
      "type=text" #> SHtml.password("", TransactionAccountUpdateAMQPListener.decyrptionPassphrase = _) &
        "type=submit" #> SHtml.submit("Enter", () => {
          logger.info("passphrase set.")
          TransactionAccountUpdateAMQPListener.startListen
          S.notice("Passphrase for decrypting sent.")
        })
    } else {
      "div *" #> "The pass phrase is already set."
    }
  }
}