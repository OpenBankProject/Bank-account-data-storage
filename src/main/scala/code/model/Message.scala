package code.model

import scala.actors._
import net.liftmodules.amqp.AMQPSender
import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftweb.mapper._

trait BankAccount{}

case class AddBankAccount(accountNumber : String, blzIban : String, pinCode : String) extends BankAccount
case class UpdateBankAccount(accountNumber : String, blzIban : String, pinCode : String) extends BankAccount
case class DeleteBankAccount(accountNumber : String, blzIban : String) extends BankAccount

class BankAccountDetails extends LongKeyedMapper[BankAccountDetails] {
  def getSingleton = BankAccountDetails

  def primaryKeyField = id
  object id extends MappedLongIndex(this)
  object accountNumber extends MappedString(this, 32)
  object blzIban extends MappedString(this, 32)
  object pinCode extends MappedString(this, 1024)
}

object BankAccountDetails extends BankAccountDetails with LongKeyedMetaMapper[BankAccountDetails]
{
  override def dbIndexes = UniqueIndex(accountNumber, blzIban) ::super.dbIndexes
}
