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
package com.tesobe.util

import dispatch._
import java.io.{File, FileInputStream}
import net.liftmodules.amqp.{AMQPAddListener,AMQPMessage, AMQPDispatcher, SerializedConsumer}
import net.liftweb.actor._
import net.liftweb.common.{Full, Box, Empty, Loggable}
import net.liftweb.json._
import net.liftweb.mapper.By
import net.liftweb.util._
import net.liftweb.util.Helpers.tryo
import scala.actors._
import scala.concurrent
import scala.concurrent.ExecutionContext.Implicits.global
import scala.util.{Failure, Success}

import com.rabbitmq.client.{ConnectionFactory,Channel}
import com.tesobe.lib.{PgpEncryption, HBCITransactionFetcher}
import com.tesobe.model.{TransactionBankAccount, BankAccountDetails, AccountConfig, OBPTransactionWrapper}

// Listens to Updates of the account data to display the relevant transactions.

class TransactionAccountUpdateAMQPDispatcher[T](factory: ConnectionFactory)
    extends AMQPDispatcher[T](factory) {
  override def configure(channel: Channel) {
    channel.exchangeDeclare("directExchange3", "direct", false)
    channel.queueDeclare("transactions", false, false, false, null)
    channel.queueBind ("transactions", "directExchange3", "transactions")
    channel.basicConsume("transactions", false, new SerializedConsumer(channel, this))
  }
}

object TransactionAccountUpdateAMQPListener extends Loggable {

  var decyrptionPassphrase = ""

  implicit val formats = DefaultFormats
  lazy val factory = new ConnectionFactory {
    import ConnectionFactory._
    setHost("localhost")
    setPort(DEFAULT_AMQP_PORT)
    setUsername(Props.get("connection.user", DEFAULT_USER))
    setPassword(Props.get("connection.password", DEFAULT_PASS))
    setVirtualHost(DEFAULT_VHOST)
  }

  val amqp = new TransactionAccountUpdateAMQPDispatcher[TransactionBankAccount](factory)

  val transactionAccountUpdateListener = new LiftActor {
    protected def messageHandler = {
      case msg@AMQPMessage(contents: TransactionBankAccount) => searchDatabaseForAccount(contents)
      case _ => {}
    }
  }

  def searchDatabaseForAccount (account: TransactionBankAccount) = {
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.bankNationalIdentifier, account.bankNationalIdentifier)) match {
      case Full(foundAccount) => {
        for {
          privateKeyPath <- Props.get("decryption.privateKeyPath")
          privateKey <- tryo { new FileInputStream (new File (privateKeyPath)) }
        } yield {
          val pinDecrypted = PgpEncryption.decrypt(foundAccount.pinCode.getBytes, privateKey, decyrptionPassphrase.toCharArray)
          val transactions = HBCITransactionFetcher.getTransactions(AccountConfig(account.bankNationalIdentifier, account.accountNumber, pinDecrypted.map(_.toChar).mkString))
          val transactionHulls = transactions.map(OBPTransactionWrapper(_))
          val json = compact(render(Extraction.decompose(transactionHulls)))
          val req =
            url(Props.get("importer.apiurl", "http://localhost:8080") +
            "/api/transactions"+
            "?secret=" + Props.get("importer.postSecret","")
            ).POST.
            setHeader("Content-Type", "application/json; charset=utf-8").
            setBody(json.getBytes("UTF-8")) // we have to give the encoding
          val result: concurrent.Future[String] = Http(req OK as.String)
          result onComplete{
            case Failure(l) => logger.error("got an error while posting to OBP: " + l.getMessage)
            case Success(r) => logger.info("posting to OBP succeeded")
          }
        }
      }
      case _ => {
        logger.warn("----- didn't find an account -----")
      }
    }
  }

  def startListen = {
    amqp ! AMQPAddListener(transactionAccountUpdateListener)
  }

}

