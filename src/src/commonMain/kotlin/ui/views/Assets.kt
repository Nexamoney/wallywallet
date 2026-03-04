package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.wrapContentHeight
import androidx.compose.foundation.lazy.LazyListState
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.text.BasicText
import androidx.compose.foundation.text.TextAutoSize
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.em
import androidx.compose.ui.unit.sp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.eygraber.uri.Url
import com.eygraber.uri.toKmpUrl
import com.ionspin.kotlin.bignum.decimal.BigDecimal
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.S
import info.bitcoinunlimited.www.wally.i18n
import info.bitcoinunlimited.www.wally.trimToNull
import info.bitcoinunlimited.www.wally.ui.WallyNumericInputFieldAsset
import info.bitcoinunlimited.www.wally.ui.theme.wallyPurple
import org.nexa.assets.AssetInfo
import info.bitcoinunlimited.www.wally.wallyApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.compose
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch
import org.nexa.libnexakotlin.GroupId
import org.nexa.libnexakotlin.rem
import org.nexa.threads.Mutex

open class AssetViewModel(val observeFocusedAccount:Boolean=true): ViewModel()
{
    val assets = MutableStateFlow(listOf<AssetInfo>())
    val amounts = MutableStateFlow(mapOf<GroupId, Long>())
    val currentPosition = MutableStateFlow(0)

    var assetsJob: Job? = null
    var accountJob: Job? = null

    init {
        if (observeFocusedAccount)
        {
            wallyApp?.focusedAccount?.value?.let {
                assets.value = getAssetInfoList(it)
            }
            observeSelectedAccount()
        }
    }

    open fun observeSelectedAccount()
    {
        accountJob?.cancel()
        accountJob = viewModelScope.launch {
            wallyApp?.focusedAccount?.onEach {
                if (it != null) observeAssets(it)
                else assets.value = listOf()
            }?.launchIn(this)
        }
    }

    open fun getAssetInfoList(account: Account): List<AssetInfo>
    {
        val assetInfoList = mutableListOf<AssetInfo>()
        account.assets.values.forEach {
            assetInfoList.add(it.assetInfo)
        }
        return assetInfoList
    }

    open fun observeAssets(account: Account)
    {
        if (observeFocusedAccount)
        {
            assetsJob?.cancel()
            assetsJob = viewModelScope.launch {
                account.assetsObservable.onEach { it ->
                    val assetInfoList = mutableListOf<AssetInfo>()
                    it.values.forEach { assetPerAccount ->
                        assetInfoList.add(assetPerAccount.assetInfo)
                    }
                    assets.value = assetInfoList
                }.launchIn(this)
            }
        }
    }

    override fun onCleared()
    {
        super.onCleared()
        accountJob?.cancel()
        assetsJob?.cancel()
    }
}

class AssetViewModelFake: AssetViewModel()
{
    override fun observeSelectedAccount()
    {

    }
    override fun getAssetInfoList(account: Account): List<AssetInfo>
    {
        return listOf()
    }
    override fun observeAssets(account: Account)
    {
    }
}

@Composable
fun AssetTinyTable(viewModel: AssetViewModel, filter: (AssetInfo, AssetViewModel)->Boolean = { a,b -> true })
{
    val assets = viewModel.assets.collectAsState().value
    val amounts = viewModel.amounts.collectAsState().value
    val assetList = assets.toList().sortedBy { it.nft?.title ?: it.name ?: it.ticker ?: it.groupId.toString() }
    val clickableModifier = Modifier  // Not clickable
    Column {
        for (asset in assetList)
        {
            if (!filter(asset,viewModel)) continue
            val assetNameState = asset.nameObservable.collectAsState()
            val nft = asset.nftObservable.collectAsState().value
            val creator = nft?.author ?: run {
                val urlS = asset.tokenInfo?.genesisInfo?.document_url
                if (urlS!=null)
                {
                    val url = runCatching { urlS.toKmpUrl() }.getOrNull()
                    url?.host
                }
                else null
            }
            val series = nft?.series?.trimToNull()
            val name = if(nft != null && nft.title.isNotEmpty()) nft.title
                        else assetNameState.value ?: asset.ticker
            Row(
              modifier = Modifier.fillMaxWidth(),
              verticalAlignment = Alignment.CenterVertically
            ) {

                val amountI = amounts[asset.groupId] ?: 0L
                val amount = asset.tokenDecimalFromFinestUnit(amountI)

                if (true)  // for now, always show the amount amountI > 1)
                {
                    Text(amount.toPlainString())
                    Spacer(Modifier.width(10.dp))
                }
                MpMediaView(asset.iconImageState.collectAsState().value, asset.iconBytes.collectAsState().value, asset.iconUri?.toString(), hideMusicView = true) { mi, draw ->
                    val m = clickableModifier.background(Color.Transparent).size(64.dp).padding(2.dp)
                    draw(m)
                }

                Spacer(Modifier.width(10.dp))

                Row (
                  modifier = clickableModifier
                ){
                    if (name == null)
                    {
                        val ts = WallyTextStyle(fw = FontWeight.Bold).copy(lineHeight = 0.9.em)
                        BasicText(asset.groupId.toStringNoPrefix(),
                              modifier = clickableModifier.fillMaxWidth().wrapContentHeight(),
                              style = ts,
                              color = { wallyPurple },
                              maxLines = 3,
                              autoSize = TextAutoSize.StepBased(8.sp, 16.sp)
                            )
                    }
                    else
                    {
                        Column(
                          modifier = clickableModifier.weight(4f),
                          verticalArrangement = Arrangement.Center
                        ) {
                            val ts = WallyTextStyle(fw = FontWeight.Bold).copy(lineHeight = 0.9.em)
                            BasicText(name,
                              modifier = clickableModifier,
                              style = ts,
                              color = { wallyPurple },
                              maxLines = 2,
                              autoSize = TextAutoSize.StepBased(8.sp, 16.sp)
                            )
                            series?.let {
                                 BasicText(text = it, modifier = clickableModifier, color = { wallyPurple }, maxLines = 1,
                                 autoSize = TextAutoSize.StepBased(8.sp, 14.sp))
                            }
                        }
                        creator?.let {
                            val ts = WallyTextStyle().copy(fontStyle = FontStyle.Italic,  lineHeight = 0.9.em, color = Color.Black, fontSize = 14.sp, textAlign = TextAlign.End)
                            BasicText(text = it,
                              modifier = clickableModifier.padding(start = 8.dp).weight(2f),
                              style = ts,
                              maxLines = 2,
                              autoSize = TextAutoSize.StepBased(6.sp, 14.sp)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun AssetCarousel(viewModel: AssetViewModel = androidx.lifecycle.viewmodel.compose.viewModel { AssetViewModel() })
{
    val assets = viewModel.assets.collectAsState().value
    val assetList = assets.toList().sortedBy { it.nft?.title ?: it.name ?: it.ticker ?: it.groupId.toString() }
    val listState = rememberSaveable(saver = LazyListState.Saver) {
        LazyListState(firstVisibleItemIndex = viewModel.currentPosition.value)
    }

    // Sync ViewModel with scroll position
    LaunchedEffect(listState.firstVisibleItemIndex) {
        viewModel.currentPosition.value = listState.firstVisibleItemIndex
    }

    LazyRow(
      modifier = Modifier.fillMaxWidth().padding(start = 0.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp),
      state = listState
    ) {

        assetList.forEachIndexed { idx, assetInfo ->
            item {
                AssetCarouselItem(assetInfo, leadSpacing = if (idx == 0) 6.dp else 0.dp)
            }
        }
    }
}