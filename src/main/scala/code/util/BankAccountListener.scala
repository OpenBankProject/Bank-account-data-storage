package code.util

// import com.rabbitmq.client.{ConnectionFactory,ConnectionParameters,Channel}
import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftmodules.amqp.{AMQPAddListener,AMQPMessage, AMQPDispatcher, SerializedConsumer}
import scala.actors._
import net.liftweb.actor._
import code.model.{BankAccount, BankAccountDetails, AddBankAccount, UpdateBankAccount, DeleteBankAccount}
import net.liftweb.common.{Full,Box,Empty}
import net.liftweb.mapper.By
import net.liftweb.util.Helpers.tryo
import net.liftweb.util._

/**
 * A dispatcher that listens on an queue and exchange.
 */
class BankAccountSerializedAMQPDispatcher[T](factory: ConnectionFactory)
    extends AMQPDispatcher[T](factory) {
  override def configure(channel: Channel) {
    // Set up the exchange and queue
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

  val amqp = new BankAccountSerializedAMQPDispatcher[BankAccount](factory) // string

  // client is not (yet) cofirming, that it got the message
  val bankAccountListener = new LiftActor {
    protected def messageHandler = {
      case msg@AMQPMessage(contents: AddBankAccount) => saveBankAccount(contents)
      case msg@AMQPMessage(contents: UpdateBankAccount) => updateBankAccount(contents)
      case msg@AMQPMessage(contents: DeleteBankAccount) => deleteBankAccount(contents)
    }
  }

  def saveBankAccount (account: AddBankAccount) : Boolean = {
    // println("add account: " + account.accountNumber)
    println(BankAccountDetails.findAll.map {
      _.id
      })
    val newAccount: BankAccountDetails = BankAccountDetails.create
    newAccount.accountNumber(account.accountNumber)
    newAccount.blzIban(account.blzIban)
    newAccount.pinCode(account.pinCode)
    tryo(newAccount.save).isEmpty

  }

  def updateBankAccount (account: UpdateBankAccount) : Boolean = {
    // println("update account: " + account.accountNumber)
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.blzIban, account.blzIban)) match {
      case Full(existingAccount) => {
        // println("id: "+existingAccount.id)
        existingAccount.pinCode(account.pinCode)
        tryo(existingAccount.save).isEmpty
      }
      case _ => false // Errormsg: Account does not exist
    }
  }

  def deleteBankAccount (account: DeleteBankAccount)= {
    // println("delete account: " + account.accountNumber)
    BankAccountDetails.find(By(BankAccountDetails.accountNumber, account.accountNumber), By(BankAccountDetails.blzIban, account.blzIban)) match {
      case Full(existingAccount) => {
        // println("id: "+existingAccount.id)
        tryo(existingAccount.delete_!).isEmpty
      }
      case _ => false // Errormsg: Account does not exist
    }
  }

  def startListen = {
    amqp ! AMQPAddListener(bankAccountListener)
  }
}

