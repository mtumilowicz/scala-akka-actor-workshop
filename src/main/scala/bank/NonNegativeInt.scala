package bank

case class NonNegativeInt(raw: Int) {
  require(raw >= 0)

  def +(other: NonNegativeInt): NonNegativeInt =
    NonNegativeInt(raw + other.raw)

  def -(other: NonNegativeInt): NonNegativeInt =
    NonNegativeInt(raw - other.raw)
}
