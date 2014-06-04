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
import net.liftweb.common.Loggable
import net.liftweb.util._
import com.rabbitmq.client.{ConnectionFactory,Channel}

import com.tesobe.model._
import com.tesobe.util.RabbitMQConnection

class BanksStatuesAMQPDispatcher[T](factory: ConnectionFactory)
    extends AMQPDispatcher[T](factory) {
  override def configure(channel: Channel) {
    channel.exchangeDeclare("getStatues", "direct", false)
    channel.queueDeclare("statuesRequest", false, false, false, null)
    channel.queueBind("statuesRequest", "getStatues", "statuesRequest")
    channel.basicConsume("statuesRequest", false, new SerializedConsumer(channel, this))
  }
}

object BanksStatuesListener extends Loggable{
  import com.tesobe.status.model.GetBanksStatues

  val amqp = new BanksStatuesAMQPDispatcher[GetBanksStatues](RabbitMQConnection.connectionFactory)

  val banksStatuesListener = new LiftActor {

    protected def messageHandler = {
      case msg@AMQPMessage(_: GetBanksStatues) => {
        import com.tesobe.model.BankLog
        import net.liftweb.mapper.{BySql, IHaveValidatedThisSQL}

        import com.tesobe.status.model.{BankStatus, BanksStatuesReply}

        logger.info("received bank statues request")
        /*
          For the moment we return only the successful logs (transactionsFetched==true)
          since the HBCI importer does not yet make the difference between a none supported
          bank and a miss configured bank account (wrong pin code, etc).
          The purpose is to avoid having wrong results.
        */

        val sqlQuery =
          s"""select Max(${BankLog.createdAt.dbColumnName}) as createdAt, ${BankLog.nationalIdentifier.dbColumnName}, ${BankLog.transactionsFetched.dbColumnName}
          from ${BankLog.dbName}
          where ${BankLog.transactionsFetched.dbColumnName}=true
          group by ${BankLog.nationalIdentifier.dbColumnName}, ${BankLog.transactionsFetched.dbColumnName} """
        val validatedQuery = IHaveValidatedThisSQL("Ayoub Benali", "24-04-2014")
        val bankLogs = BankLog.findAllByInsecureSql(sqlQuery, validatedQuery)

        //TODO: support multi country banks
        val statues = bankLogs.map(l=>
          BankStatus(
            "DEU",
            l.nationalIdentifier,
            true,
            l.createdAt
          )
        )
        val message = BanksStatuesReply(statues.toSet)

        ResponseSender.sendStatues(message)

      }
    }
  }

  def startListen = {
    logger.info("listening of banks statues messages")
    amqp ! AMQPAddListener(banksStatuesListener)
  }
}

