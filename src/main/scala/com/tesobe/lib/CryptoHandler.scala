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
 package com.tesobe.lib

import java.io._
import scala.sys.process._
import net.liftweb.util.Helpers._
import net.liftweb.util.Props
import net.liftweb.common._

/**
 * Provides functionality to decrypt a given encrypted password.
 */
trait CryptoHandler extends Loggable {
  def decryptPin(data: String, passphrase: String): Box[String] = {
    val pinBox = tryo {
      val p = new PGPUtils
      /*! Create an input stream for the encrypted data. */
      val in = new ByteArrayInputStream(data.getBytes())
      /*! Now create an input stream for the private key data. This key
          is expected to live in a file that's specified in the props file.
          We can obtain such a file by saying
          `gpg --export-secret-keys {key-id} > key.gpg` */
      val keyIn = new FileInputStream(Props.get("importer.keyfile", "key.gpg"))
      /*! Create a stream where the output data (the decrypted data)
          will go to. */
      val out = new ByteArrayOutputStream

      /*! Decrypt the data and return the whitespace-trimmed string */
      PGPUtils.decryptFile(in, out, keyIn, passphrase.toCharArray)
      out.toString.trim
    }
    pinBox match {
      case Full(pin) => Full(pin)
      case Failure(msg, ex, x) => Failure("error while decrypting file: " + msg, ex, x)
      case Empty => Empty
    }
  }
}