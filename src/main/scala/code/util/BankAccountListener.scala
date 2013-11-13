package code.util


import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftmodules.amqp.{AMQPAddListener,AMQPMessage, AMQPDispatcher, SerializedConsumer}
import scala.actors._
import net.liftweb.actor._
import code.model.{BankAccount, BankAccountDetails, AddBankAccount, UpdateBankAccount, DeleteBankAccount, SuccessResponse,ErrorResponse}
import net.liftweb.common.{Full,Box,Empty}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util._

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
    // localhost is a machine on your network with rabbitmq listening on port 5672
    setHost("localhost")
    setPort(DEFAULT_AMQP_PORT)
    setUsername(Props.get("connection.user", DEFAULT_USER))
    setPassword(Props.get("connection.password", DEFAULT_PASS))
    setVirtualHost(DEFAULT_VHOST)
  }

  val amqp = new BankAccountSerializedAMQPDispatcher[BankAccount](factory)

  val bankAccountListener = new LiftActor {
    protected def messageHandler = {
      case msg@AMQPMessage(contents: AddBankAccount) => saveBankAccount(contents)
      case msg@AMQPMessage(contents: UpdateBankAccount) => updateBankAccount(contents)
      case msg@AMQPMessage(contents: DeleteBankAccount) => deleteBankAccount(contents)
    }
  }

  def saveBankAccount (account: AddBankAccount) : Boolean = {
    val newAccount: BankAccountDetails = BankAccountDetails.create
    newAccount.accountNumber(account.accountNumber)
    newAccount.blzIban(account.blzIban)
    newAccount.pinCode(account.pinCode)
    val saved = !tryo(newAccount.save).isEmpty
    if (saved)
      ResponseSender.sendMessage(SuccessResponse(account.id,"account saved"))
    else
      ResponseSender.sendMessage(ErrorResponse(account.id,"account already exists"))
    saved

  }

  def updateBankAccount (account: UpdateBankAccount) : Boolean = {
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.blzIban, account.blzIban)) match {
      case Full(existingAccount) => {
        existingAccount.pinCode(account.pinCode)
        val updated = !tryo(existingAccount.save).isEmpty
        if (updated)
          ResponseSender.sendMessage(SuccessResponse(account.id,"account updated"))
        else
          ResponseSender.sendMessage(ErrorResponse(account.id,"account not updated"))
        updated
      }
      case _ => {
        ResponseSender.sendMessage(ErrorResponse(account.id,"account does not exist"))
        false
      }
    }
  }

  def deleteBankAccount (account: DeleteBankAccount) : Boolean = {
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.blzIban, account.blzIban)) match {
      case Full(existingAccount) => {
        var deleted = !tryo(existingAccount.delete_!).isEmpty
        if (deleted)
          ResponseSender.sendMessage(SuccessResponse(account.id,"account deleted"))
        else
          ResponseSender.sendMessage(ErrorResponse(account.id,"account not deleted"))
        deleted
      }
      case _ => {
        ResponseSender.sendMessage(ErrorResponse(account.id,"account does not exist"))
        false
      }
    }
  }

  def startListen = {
    amqp ! AMQPAddListener(bankAccountListener)
  }
}

