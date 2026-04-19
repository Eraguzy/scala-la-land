package functions

import java.security.MessageDigest
import objects.UnsignedTransaction
import java.math.BigInteger
import java.util.Random

object Utils {
  def genPrime(): BigInt = BigInt(BigInteger.probablePrime(256, new Random()))

  def genTwoDiffPrimes(): (BigInt, BigInt) = {
    val prime1 = genPrime()
    def nextDistinctPrime(): BigInt = {
      val candidate = genPrime()
      if (candidate == prime1) nextDistinctPrime() else candidate
    }
    val prime2 = nextDistinctPrime() // ensure we have 2 distinct primes
    (prime1, prime2)
  }

  def findE(phi: BigInt): Option[BigInt] = {
    var e = BigInt(3)

    while (e < phi) {
      if (e.gcd(phi) == 1) return Some(e)
      e += 2 // we can skip even numbers
    }
    None // should never happen for large phi
  }
}
