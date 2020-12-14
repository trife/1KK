package org.wheatgenetics.onekk.fragments

import android.content.Context.MODE_PRIVATE
import android.graphics.BitmapFactory
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.*
import android.widget.TextView
import android.widget.Toast
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import com.polidea.rxandroidble2.helpers.ValueInterpreter
import kotlinx.android.synthetic.main.fragment_contour_list.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.MainScope
import org.wheatgenetics.onekk.R
import org.wheatgenetics.onekk.database.OnekkDatabase
import org.wheatgenetics.onekk.database.OnekkRepository
import org.wheatgenetics.onekk.database.viewmodels.ExperimentViewModel
import org.wheatgenetics.onekk.database.viewmodels.factory.OnekkViewModelFactory
import org.wheatgenetics.onekk.databinding.FragmentScaleBinding
import org.wheatgenetics.onekk.interfaces.BleNotificationListener
import org.wheatgenetics.utils.BluetoothUtil
import java.lang.IllegalStateException
import java.lang.IndexOutOfBoundsException
import java.util.*
import kotlin.properties.Delegates

//TODO rework scale message queue / handler
/**
 * This fragment was developed under the guidance of the following reference:
 * https://developer.android.com/guide/topics/connectivity/bluetooth-le
 * and http://polidea.github.io/RxAndroidBle/
 * The scale fragment's purpose is to interface with Ohaus scales to weigh seed samples
 *  that have been counted in OneKK.
 */
class ScaleFragment : Fragment(), CoroutineScope by MainScope(), BleNotificationListener {

    private val viewModel by viewModels<ExperimentViewModel> {
        with(OnekkDatabase.getInstance(requireContext())) {
            OnekkViewModelFactory(OnekkRepository.getInstance(this.dao(), this.coinDao()))
        }
    }

    private var aid by Delegates.notNull<Int>()

    private val mPreferences by lazy {
        requireContext().getSharedPreferences(getString(R.string.onekk_preference_key), MODE_PRIVATE)
    }

    private val mBluetoothManager by lazy {
        BluetoothUtil(requireContext())
    }

    companion object {

        val OHAUS_BLUETOOTH_GATT_SERVICE_UUID = "2456e1b9-26e2-8f83-e744-f34f01e9d701"

        val OHAUS_BLUETOOTH_GATT_DESCRIPTOR_UUID = "00002902-0000-1000-8000-00805f9b34fb"

        val OHAUS_BLUETOOTH_GATT_CHARACTERISTIC_UUID = "2456e1b9-26e2-8f83-e744-f34f01e9d703"

        val OHAUS_BLUETOOTH_GATT_CHARACTERISTIC_UUID_B = "2456e1b9-26e2-8f83-e744-f34f01e9d704"
        
        final val TAG = "Onekk.AnalysisFragment"

        private const val FILENAME_FORMAT = "yyyy-MM-dd-HH-mm-ss.SSS"
    }

    //global references to the menu, allows the swapping of icons when connecting/disconnecting from bt
    private var mMenu: Menu? = null

    private var mBinding: FragmentScaleBinding? = null


    /**
     * The interface implementation which is sent to setupDeviceComms
     * This will read any notification that is received from the device.
     */
    override fun onNotification(bytes: ByteArray) {

        val stringResult = ValueInterpreter.getStringValue(bytes, 0)

        if (stringResult.isNotBlank()) {

            scaleTextUpdateUi(stringResult)

        }

    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        aid = requireArguments().getInt("analysis", -1)

        startMacAddressSearch()

    }

    override fun onCreateView(inflater: LayoutInflater, container: ViewGroup?,
                              savedInstanceState: Bundle?): View? {

        mBinding = DataBindingUtil.inflate(inflater, R.layout.fragment_scale, container, false)

        with(mBinding) {

            this?.scaleCaptureButton?.setOnClickListener {

                viewModel.updateAnalysisWeight(aid, this.scaleEditText.text?.toString()?.toDoubleOrNull())

                findNavController().navigate(ScaleFragmentDirections.actionToCamera())
            }

            viewModel.getSourceImage(aid).observeForever { url ->

                imageView?.setImageBitmap(BitmapFactory.decodeFile(url))

            }

        }

        setHasOptionsMenu(true)

        return mBinding?.root
    }

    private fun startMacAddressSearch() {

        val macAddress = mPreferences.getString(getString(R.string.preferences_enable_bluetooth_key), null)

        if (macAddress != null) {

            BluetoothUtil(requireContext()).establishConnectionToAddress(this, macAddress)

        } else {

            //TODO: Instead of moving to Settings, the service can be automatically found (if it's available)
            Toast.makeText(requireContext(), getString(R.string.frag_scale_no_mac_address_found_message), Toast.LENGTH_LONG).show()

            findNavController().navigate(ScaleFragmentDirections.actionToSettings())

        }
    }

    override fun onCreateOptionsMenu(menu: Menu, inflater: MenuInflater) {

        inflater.inflate(R.menu.scale_toolbar, menu)

        mMenu = menu

        super.onCreateOptionsMenu(menu, inflater)
    }

    override fun onOptionsItemSelected(item: MenuItem): Boolean {

        with(mBinding) {

            when(item.itemId) {

                R.id.action_print -> {

                    startMacAddressSearch()

                }
                else -> null
            }
        }

        return super.onOptionsItemSelected(item)
    }

    /**
     * Disposables are destroyed so BLE connections are lost when the app is sent to background.
     */
    override fun onPause() {
        super.onPause()

        mBluetoothManager.dispose()

    }

    /**
     * Function that updates the scale measurement UI which can be called from other threads.
     */
    //TODO: use ohaus commands to format the output text to not include newlines.
    private fun scaleTextUpdateUi(value: String) {

        activity?.let {

            it.runOnUiThread {

                it.findViewById<TextView>(R.id.scaleEditText)?.text = formatWeightText(value)

            }
        }
    }

    private fun formatWeightText(text: String): String = text.replace("\n", "")
            .split("g")[0]
            .replace(" ", "")

}