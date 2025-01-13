package info.bitcoinunlimited.www.wally

import org.nexa.libnexakotlin.rem

class ShoppingDestination(var buttonText: String = "", var explain: String = "", var url: String = "", var androidPackage: String = "", var icon: String? = null, val destinationType: DestinationType = DestinationType.OTHER)
{
}

enum class DestinationType
{
    EXCHANGE, OTHER
}

val initialShopping: ArrayList<ShoppingDestination> = if (platform().hasLinkToNiftyArt)
    arrayListOf(
      /*
      ShoppingDestination(i18n(R.string.GiftCardButton), i18n(R.string.ExplainGiftCards), i18n(R.string.GiftCardUrl), i18n(R.string.GiftCardAppPackage), R.mipmap.ic_egifter),
      ShoppingDestination(i18n(R.string.RestaurantButton), i18n(R.string.ExplainRestaurant), i18n(R.string.RestaurantUrl), i18n(R.string.RestaurantAppPackage), R.mipmap.ic_menufy),
      ShoppingDestination(i18n(R.string.StoreMapButton), i18n(R.string.ExplainStoreMap), i18n(R.string.StoreMapUrl), i18n(R.string.StoreMapAppPackage))
       */
      ShoppingDestination(i18n(S.NFTs), i18n(S.ExplainNFTs), i18n(S.NftUrl), "", "icons/niftyart.png" ), // R.drawable.ic_niftyart_logo_plain),
      ShoppingDestination(i18n(S.CexButton), i18n(S.ExplainBitmart), "https://www.bitmart.com/trade/en-US?symbol=NEXA_USDT", "","icons/bitmart.png"),
      ShoppingDestination(i18n(S.CexButton), i18n(S.ExplainMexc), "https://www.mexc.com/exchange/NEXA_USDT", "", "icons/mexc.png"),
      ShoppingDestination(i18n(S.CexButton), i18n(S.ExplainBiconomy), "https://www.biconomy.com/exchange/NEXA_USDT", "", "icons/biconomy.png")
    )
else
    arrayListOf(
      /*
      ShoppingDestination(i18n(R.string.GiftCardButton), i18n(R.string.ExplainGiftCards), i18n(R.string.GiftCardUrl), i18n(R.string.GiftCardAppPackage), R.mipmap.ic_egifter),
      ShoppingDestination(i18n(R.string.RestaurantButton), i18n(R.string.ExplainRestaurant), i18n(R.string.RestaurantUrl), i18n(R.string.RestaurantAppPackage), R.mipmap.ic_menufy),
      ShoppingDestination(i18n(R.string.StoreMapButton), i18n(R.string.ExplainStoreMap), i18n(R.string.StoreMapUrl), i18n(R.string.StoreMapAppPackage))
      ShoppingDestination(i18n(S.NFTs), i18n(S.ExplainNFTs), i18n(S.NftUrl), "", "icons/niftyart.png" ), // R.drawable.ic_niftyart_logo_plain),
       */
      ShoppingDestination("https://www.bitmart.com/trade/en-US?symbol=NEXA_USDT", i18n(S.ExplainBitmart),"https://www.bitmart.com/trade/en-US?symbol=NEXA_USDT", "","icons/bitmart.png", destinationType = DestinationType.EXCHANGE),
      ShoppingDestination("https://www.mexc.com/exchange/NEXA_USDT", i18n(S.ExplainMexc), "https://www.mexc.com/exchange/NEXA_USDT", "", "icons/mexc.png", destinationType = DestinationType.EXCHANGE),
      ShoppingDestination("https://www.biconomy.com/exchange/NEXA_USDT", i18n(S.ExplainBiconomy), "https://www.biconomy.com/exchange/NEXA_USDT", "", "icons/biconomy.png")
    )