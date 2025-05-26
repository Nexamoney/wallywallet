package info.bitcoinunlimited.www.wally.ui.views

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import info.bitcoinunlimited.www.wally.Account
import info.bitcoinunlimited.www.wally.AssetInfo
import info.bitcoinunlimited.www.wally.wallyApp
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.launch

open class AssetViewModel: ViewModel()
{
    val assets = MutableStateFlow(listOf<AssetInfo>())
    var assetsJob: Job? = null
    var accountJob: Job? = null

    init {
        wallyApp?.focusedAccount?.value?.let {
            assets.value = getAssetInfoList(it)
        }
        observeSelectedAccount()
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
fun AssetCarousel(viewModel: AssetViewModel = androidx.lifecycle.viewmodel.compose.viewModel { AssetViewModel() })
{
    val assets = viewModel.assets.collectAsState().value
    val assetList = assets.toList().sortedBy { it.nft?.title ?: it.name ?: it.ticker ?: it.groupId.toString() }

    LazyRow(
      modifier = Modifier.fillMaxWidth().padding(start = 0.dp),
      horizontalArrangement = Arrangement.spacedBy(6.dp)
    ) {

        assetList.forEachIndexed { idx,assetInfo ->
            item {
                AssetCarouselItem(assetInfo, leadSpacing = if (idx == 0) 6.dp else 0.dp)
            }
        }
    }
}