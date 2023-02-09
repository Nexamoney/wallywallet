package info.bitcoinunlimited.www.wally

import android.content.Intent
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import androidx.fragment.app.Fragment
import androidx.navigation.fragment.findNavController
import info.bitcoinunlimited.www.wally.databinding.*

class Welcome : CommonNavActivity()
{
    private lateinit var ui:ActivityWelcomeBinding
    var walletCreated = false
    var enteredIdentity = false

    override fun onCreate(savedInstanceState: Bundle?)
    {
        super.onCreate(savedInstanceState)
        ui = ActivityWelcomeBinding.inflate(layoutInflater)
        setContentView(ui.root)
        val app = (getApplication() as WallyApp)
        app.firstRun = false  // Stops jumping back into the welcome if we've left it

        //loadFragment(WelcomeFragment1())
    }

    /*  Since I'm using the fragment nav, this isn't needed but keeping for possible use later
    fun loadFragment(fragment: Fragment)
    {
        // create a FragmentManager
        val fm: FragmentManager = supportFragmentManager
        // create a FragmentTransaction to begin the transaction and replace the Fragment
        val fragmentTransaction: FragmentTransaction = fm.beginTransaction()
        // replace the FrameLayout with new Fragment
        fragmentTransaction.replace(R.id.welcomeStep1, fragment)
        fragmentTransaction.commit() // save the changes
    }
     */
}


/**
 *
 */
class WelcomeFragment1 : Fragment()
{
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        return Welcomef1Binding.inflate(inflater).root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_Welcome1_to_Welcome2)
        }
    }
}

class WelcomeFragment2 : Fragment()
{
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        return Welcomef2Binding.inflate(inflater).root
    }

    override fun onResume()
    {
        super.onResume()
        if (wallyApp!!.accounts.isNotEmpty())
        {
            findNavController().navigate(R.id.action_NewAccount_to_Backup)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)
        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            if (wallyApp!!.accounts.isEmpty())
            {
                val intent = Intent(appContext!!.context, NewAccount::class.java)
                startActivity(intent)
            }
            else
            {
                findNavController().navigate(R.id.action_NewAccount_to_Backup)
            }
        }
    }
}

class WelcomeFragment3 : Fragment()
{
    public lateinit var ui: Welcomef3Binding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        ui = Welcomef3Binding.inflate(inflater)
        return ui.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        super.onViewCreated(view, savedInstanceState)

        val accounts = wallyApp!!.accounts
        var wordSeeds: String = ""
        for (c in accounts)  // grab the 1st primary account
        {
            if (c.value.wallet.chainSelector == PRIMARY_CRYPTO)
            {
                wordSeeds = c.value.wallet.secretWords + "\n"
                break
            }
        }
        if (wordSeeds == "") wordSeeds = accounts.values.first().wallet.secretWords
        ui.welcomeWalletBackupSecret.text = wordSeeds

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            findNavController().navigate(R.id.action_Backup_to_Identity)
        }
    }
}

class WelcomeIdentity : Fragment()
{
    public lateinit var ui: Welcomef4Binding
    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?, savedInstanceState: Bundle?): View
    {
        ui = Welcomef4Binding.inflate(inflater)
        return ui.root
    }

    override fun onResume()
    {
        super.onResume()
        val act = activity as Welcome
        if (act.enteredIdentity)
        {
            ui.welcomeIdentityText.text = i18n(R.string.welcomeComplete)
        }
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?)
    {
        val act = activity as Welcome
        super.onViewCreated(view, savedInstanceState)

        view.findViewById<Button>(R.id.button_first).setOnClickListener {
            if (!act.enteredIdentity) // wallyApp!!.accounts.size==0)
            {
                val intent = Intent(appContext!!.context, IdentitySettings::class.java)
                act.enteredIdentity = true
                startActivity(intent)
            }
            else
            {
                act.finish()
            }
        }
    }
}