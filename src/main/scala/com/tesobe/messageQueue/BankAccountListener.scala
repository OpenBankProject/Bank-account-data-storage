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
package com.tesobe.messageQueue

import net.liftmodules.amqp.{AMQPAddListener,AMQPMessage, AMQPDispatcher, SerializedConsumer}
import net.liftweb.actor._
import net.liftweb.common.{Full,Loggable}
import net.liftweb.mapper.By
import net.liftweb.util._
import net.liftweb.util.Helpers.tryo
import com.rabbitmq.client.{ConnectionFactory,Channel}

import com.tesobe.model._
import com.tesobe.util.RabbitMQConnection

class BankAccountSerializedAMQPDispatcher[T](factory: ConnectionFactory)
    extends AMQPDispatcher[T](factory) {
  override def configure(channel: Channel) {
    channel.exchangeDeclare("directExchange", "direct", false)
    channel.queueDeclare("management", false, false, false, null)
    channel.queueBind ("management", "directExchange", "management")
    channel.basicConsume("management", false, new SerializedConsumer(channel, this))
  }
}

object BankAccountAMQPListener extends Loggable{

  val amqp = new BankAccountSerializedAMQPDispatcher[BankAccount](RabbitMQConnection.connectionFactory)

  val bankAccountListener = new LiftActor {
    protected def messageHandler = {
      case msg@AMQPMessage(contents: AddBankAccountCredentials) => saveBankAccount(contents)
    }
  }

  def saveBankAccount (account: AddBankAccountCredentials)= {
    logger.info(s"received message: $account")
    //Send a message to the API only if the account was created
    val accountExistsOrCreated =
      BankAccountDetails.find(
        By(BankAccountDetails.accountNumber, account.accountNumber),
        By(BankAccountDetails.bankNationalIdentifier, account.bankNationalIdentifier)
      ) match {
        case Full(b) => {
          logger.info(s"account ${account.accountNumber} / ${account.bankNationalIdentifier} credentials found. Updating the pincode")
          b.pinCode(account.pinCode)
          tryo(b.save).isDefined
        }
        case _ => {
          logger.info("creating Bank Account Details")
          val newAccount = BankAccountDetails.create
          newAccount.accountNumber(account.accountNumber)
          newAccount.bankNationalIdentifier(account.bankNationalIdentifier)
          newAccount.pinCode(account.pinCode)
          tryo(newAccount.save).isDefined
        }
      }

    if (accountExistsOrCreated){
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

  def startListen = {
    logger.info("listening of add bank account details messages")
    amqp ! AMQPAddListener(bankAccountListener)
  }
}

