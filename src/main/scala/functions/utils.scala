package functions

import java.math.BigInteger
import java.util.Random

object Utils {

  def genPrime(): BigInt = BigInt(BigInteger.probablePrime(256, new Random()))

  def genTwoDiffPrimes(): (BigInt, BigInt) = {
    val p = genPrime()
    // RSA is completely broken if p == q, so we keep generating until they differ
    def distinct(): BigInt = { val c = genPrime(); if (c == p) distinct() else c }
    (p, distinct())
  }

  def findE(phi: BigInt): Option[BigInt] = {
    var e = BigInt(3)
    // e must be odd (even numbers can't be coprime with phi, which is always even)
    while (e < phi) {
      if (e.gcd(phi) == 1) return Some(e)
      e += 2
    }
    None
  }
}
