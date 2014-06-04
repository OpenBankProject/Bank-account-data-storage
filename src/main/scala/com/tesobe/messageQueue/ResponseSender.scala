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

import net.liftmodules.amqp.{AMQPSender,StringAMQPSender,AMQPMessage}
import net.liftweb.util._
import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftweb.common.Loggable

import com.tesobe.model.{Response, CreateBankAccount}
import com.tesobe.util.RabbitMQConnection


// allows the application to write messages in the message queue
object ResponseSender extends Loggable{

  // StringAMQPSender(ConnectionFactory, EXCHANGE, QUEUE_ROUTING_KEY)
  val amqp1 = new StringAMQPSender(RabbitMQConnection.connectionFactory, "directExchange2", "response")
  val amqp2 = new StringAMQPSender(RabbitMQConnection.connectionFactory, "directExchange4", "createBankAccount")
  val amqp3 = new StringAMQPSender(RabbitMQConnection.connectionFactory, "bankStatuesResponse", "bankStatues")


  def sendMessageForWebApp(response: Response) = {
    logger.info(s"sending to web application : $response")
    amqp1 ! AMQPMessage(response)
  }

  def sendMessageForAPI(response: CreateBankAccount) = {
    logger.info(s"sending to API : $response")
     amqp2 ! AMQPMessage(response)
  }

  import com.tesobe.status.model.BanksStatuesReply
  def sendStatues(statues: BanksStatuesReply) = {
    logger.info(s"sending to status application: $statues")
    amqp3 ! AMQPMessage(statues)
  }
}
