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
package com.tesobe.util

import net.liftmodules.amqp.{AMQPAddListener,AMQPMessage, AMQPDispatcher, SerializedConsumer}
import net.liftweb.actor._
import net.liftweb.common.{Full,Box,Empty}
import net.liftweb.mapper.By
import net.liftweb.util._
import net.liftweb.util.Helpers.tryo
import org.kapott.hbci.manager.HBCIUtils
import scala.actors._

import com.rabbitmq.client.{ConnectionFactory,Channel}
import com.tesobe.model._
// Listens to management queue to create, update or delete accounts in the database.

class BankAccountSerializedAMQPDispatcher[T](factory: ConnectionFactory)
    extends AMQPDispatcher[T](factory) {
  override def configure(channel: Channel) {
    channel.exchangeDeclare("directExchange", "direct", false)
    channel.queueDeclare("management", false, false, false, null)
    channel.queueBind ("management", "directExchange", "management")
    channel.basicConsume("management", false, new SerializedConsumer(channel, this))
  }
}

object BankAccountAMQPListener {
  lazy val factory = new ConnectionFactory {
    import ConnectionFactory._
    setHost(Props.get("connection.host","localhost"))
    setPort(DEFAULT_AMQP_PORT)
    setUsername(Props.get("connection.user", DEFAULT_USER))
    setPassword(Props.get("connection.password", DEFAULT_PASS))
    setVirtualHost(DEFAULT_VHOST)
  }

  val amqp = new BankAccountSerializedAMQPDispatcher[BankAccount](factory)

  val bankAccountListener = new LiftActor {
    protected def messageHandler = {
      case msg@AMQPMessage(contents: AddBankAccountCredentials) => saveBankAccount(contents)
      case msg@AMQPMessage(contents: UpdateBankAccountCredentials) => updateBankAccount(contents)
      case msg@AMQPMessage(contents: DeleteBankAccountCredentials) => deleteBankAccount(contents)
    }
  }

  def saveBankAccount (account: AddBankAccountCredentials)= {

    //Send a message to the API only if the account was created
    val respond =
      BankAccountDetails.find(
        By(BankAccountDetails.accountNumber, account.accountNumber),
        By(BankAccountDetails.bankNationalIdentifier, account.bankNationalIdentifier),
      ) match {
        case Full(b) => true
        case _ => {
          val newAccount = BankAccountDetails.create
          newAccount.accountNumber(account.accountNumber)
          newAccount.bankNationalIdentifier(account.bankNationalIdentifier)
          newAccount.pinCode(account.pinCode)
          !tryo(newAccount.save).isEmpty
        }
      }

    //on the account
    if (respond){
      ResponseSender.sendMessageForWebApp(SuccessResponse(account.id,"account saved"))
      ResponseSender.sendMessageForAPI(
        CreateBankAccount(
          account.accountOwnerId,
          account.accountOwnerProvider,
          account.accountNumber,
          account.bankNationalIdentifier,
          account.bankName
        )
      )
    }
    else
      ResponseSender.sendMessageForWebApp(ErrorResponse(account.id,"could not save the account"))
  }

  def updateBankAccount (account: UpdateBankAccountCredentials) : Boolean = {
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.bankNationalIdentifier, account.bankNationalIdentifier)) match {
      case Full(existingAccount) => {
        existingAccount.pinCode(account.pinCode)
        val updated = !tryo(existingAccount.save).isEmpty
        if (updated)
          ResponseSender.sendMessageForWebApp(SuccessResponse(account.id,"account updated"))
        else
          ResponseSender.sendMessageForWebApp(ErrorResponse(account.id,"account not updated"))
        updated
      }
      case _ => {
        ResponseSender.sendMessageForWebApp(ErrorResponse(account.id,"account does not exist"))
        false
      }
    }
  }

  def deleteBankAccount (account: DeleteBankAccountCredentials) : Boolean = {
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.bankNationalIdentifier, account.bankNationalIdentifier)) match {
      case Full(existingAccount) => {
        var deleted = !tryo(existingAccount.delete_!).isEmpty
        if (deleted)
          ResponseSender.sendMessageForWebApp(SuccessResponse(account.id,"account deleted"))
        else
          ResponseSender.sendMessageForWebApp(ErrorResponse(account.id,"account not deleted"))
        deleted
      }
      case _ => {
        ResponseSender.sendMessageForWebApp(ErrorResponse(account.id,"account does not exist"))
        false
      }
    }
  }

  def startListen = {
    amqp ! AMQPAddListener(bankAccountListener)
  }
}

