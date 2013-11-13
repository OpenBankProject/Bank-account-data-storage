package code.model

import scala.actors._
import net.liftmodules.amqp.AMQPSender
import com.rabbitmq.client.{ConnectionFactory,Channel}
import net.liftweb.mapper._


trait BankAccount{}

case class AddBankAccount(val id: String, val accountNumber : String, val blzIban : String, val pinCode : String) extends BankAccount
case class UpdateBankAccount(val id: String, val accountNumber : String, val blzIban : String, val pinCode : String) extends BankAccount
case class DeleteBankAccount(val id: String, val accountNumber : String, val blzIban : String) extends BankAccount

trait Response{
  val id: String
  val message: String
}

case class SuccessResponse(val id: String, val message: String) extends Response
case class ErrorResponse(val id: String, val message: String) extends Response

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
