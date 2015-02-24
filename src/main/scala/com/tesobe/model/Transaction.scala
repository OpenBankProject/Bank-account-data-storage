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
package com.tesobe.model

import java.util.Date
/**
 * Holds the configuration of an account.
 *
 * @param bank the bank code of this account (e.g. "10010010" for Postbank)
 * @param account the account number of this account (e.g. "12345")
 * @param pinData the encrypted PIN for this account
 */
case class AccountConfig(
  bankNationalIdentifier: String,
  accountNumber: String,
  userId: Option[String],
  pin: String
)
/**
 * Holds the transaction data that is to be pushed to the OBP API.
 */
case class OBPTransactionWrapper(
  obp_transaction: OBPTransaction)

case class OBPTransaction(
  this_account: OBPAccount,
  other_account: OBPAccount,
  details: OBPDetails)

case class OBPAccount(
  holder: String,
  number: String,
  kind: String,
  bank: OBPBank
)

//IBAN is in here rather than the OBPAccount for legacy reasons
case class OBPBank(
  IBAN: String,
  national_identifier: String,
  name: String
)

case class OBPDetails(
  kind: String,
  posted: OBPDate,
  completed: OBPDate,
  new_balance: OBPAmount,
  value: OBPAmount,
  label: String)

case class OBPDate(`$dt`: Date)

case class OBPAmount(
  currency: String,
  amount: String) {
  override def toString = "OBPAmount(" + currency + ",***)"
}

