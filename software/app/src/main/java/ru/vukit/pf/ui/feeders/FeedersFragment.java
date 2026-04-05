package ru.vukit.pf.ui.feeders;

import android.annotation.SuppressLint;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.BaseAdapter;
import android.widget.ListView;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.fragment.app.ListFragment;
import androidx.navigation.NavController;
import androidx.navigation.fragment.NavHostFragment;

import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Objects;

import ru.vukit.pf.R;
import ru.vukit.pf.databinding.FragmentFeedersBinding;

public class FeedersFragment extends ListFragment {

    final FeedersState feedersState = FeedersState.getInstance();
    public static final String feederName = "VPF";
    private FragmentFeedersBinding binding;
    private FeedersAdapter feedersAdapter;
    public boolean wasThereNewScan;
    ru.vukit.pf.bluetooth.Driver btDriver;
    ru.vukit.pf.database.Driver dbDriver;
    final Handler handler = new Handler();

    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        binding = FragmentFeedersBinding.inflate(inflater, container, false);
        View root = binding.getRoot();

        binding.fabSearchFeeders.setOnClickListener(view -> {
            if (btDriver.isScanning()) {
                btDriver.stopScanLeDevice(scannerCallback);
            } else {
                handler.post(runnableSearchFeeders);
            }
        });

        return root;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    @Override
    public void onResume() {
        super.onResume();
        wasThereNewScan = false;
    }

    @Override
    public void onStart() {
        super.onStart();
        dbDriver = ru.vukit.pf.database.Driver.getInstance(requireContext());
        btDriver = ru.vukit.pf.bluetooth.Driver.getInstance(requireContext());
        feedersAdapter = new FeedersAdapter(getActivity(), R.layout.feeder_list_item, feedersState.feeders);
        setListAdapter(feedersAdapter);
    }

    @Override
    public void onStop() {
        handler.removeCallbacks(runnableSearchFeeders);
        if (btDriver.isScanning()) {
            btDriver.stopScanLeDevice(scannerCallback);
        }
        feedersAdapter = null;
        super.onStop();
    }

    @Override
    public void onListItemClick(@NonNull ListView l, @NonNull View v, int position, long id) {
        super.onListItemClick(l, v, position, id);
        final NavHostFragment navHostFragment = (NavHostFragment) requireActivity().getSupportFragmentManager().findFragmentById(R.id.nav_host_fragment_content_main);
        if (navHostFragment != null) {
            final NavController navController = navHostFragment.getNavController();
            Bundle selectedFeeder = new Bundle();
            selectedFeeder.putString("name", feedersState.feeders.get(position).get("name"));
            selectedFeeder.putString("address", feedersState.feeders.get(position).get("address"));
            selectedFeeder.putBoolean("wasThereNewScan", wasThereNewScan);
            navController.navigate(R.id.nav_feeder, selectedFeeder);
        }
    }

    final Runnable runnableSearchFeeders = new Runnable() {
        @Override
        public void run() {
            if (btDriver.isEnabled()) {
                if (binding.empty.getText() == getString(R.string.press_search_button) || binding.empty.getText() == getString(R.string.no_feeders_found)) {
                    feedersState.feeders.clear();
                    if (feedersAdapter != null) {
                        feedersAdapter.notifyDataSetChanged();
                    }
                    binding.empty.setText(R.string.searching);
                    binding.searchProgressIndicator.setVisibility(View.VISIBLE);
                    btDriver.startScanLeDevice(scannerCallback);
                    wasThereNewScan = true;
                }
                if (!btDriver.isScanning()) {
                    if (feedersState.feeders.isEmpty()) {
                        binding.empty.setText(R.string.no_feeders_found);
                    } else {
                        binding.empty.setText(R.string.press_search_button);
                    }
                    binding.searchProgressIndicator.setVisibility(View.GONE);
                    return;
                }
            }
            handler.postDelayed(this, 100);
        }
    };

    final private ScanCallback scannerCallback = new ScanCallback() {
        @SuppressLint("MissingPermission")
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            BluetoothDevice device = result.getDevice();
            if (device.getName() == null || !Objects.equals(device.getName(), feederName)) {
                return;
            }
            String feederName = device.getName();
            boolean isNewFeeder = true;
            for (HashMap<String, String> feeder : feedersState.feeders)
                if (feeder.containsValue(device.getAddress())) {
                    isNewFeeder = false;
                    feeder.put("rssi", String.format(Locale.getDefault(), "%3d", result.getRssi()));
                    break;
                }
            if (isNewFeeder) {
                HashMap<String, String> dataMap = new HashMap<>();
                HashMap<String, String> dbFeeder = dbDriver.selectFeeder(device.getAddress());
                if (dbFeeder != null) {
                    dataMap.put("name", dbFeeder.get("name"));
                } else {
                    dbDriver.createFeeder(device.getAddress(), feederName);
                    dataMap.put("name", feederName);
                }
                dataMap.put("address", device.getAddress());
                dataMap.put("rssi", String.format(Locale.getDefault(), "%3d", result.getRssi()));
                feedersState.feeders.add(0, dataMap);
            }
            if (feedersAdapter != null) {
                feedersAdapter.notifyDataSetChanged();
            }
        }
    };

    private static class FeedersAdapter extends BaseAdapter {
        final private Context ctx;
        final int resource;
        final private List<HashMap<String, String>> feeders;

        FeedersAdapter(Context ctx, int resource, List<HashMap<String, String>> feeders) {
            this.ctx = ctx;
            this.resource = resource;
            this.feeders = feeders;
        }

        @Override
        public int getCount() {
            return this.feeders.size();
        }

        @Override
        public Object getItem(int position) {
            return this.feeders.get(position);
        }

        @Override
        public long getItemId(int position) {
            return position;
        }

        @Override
        public View getView(int position, View convertView, ViewGroup parent) {
            LayoutInflater inflater = (LayoutInflater) ctx.getSystemService(Context.LAYOUT_INFLATER_SERVICE);
            View row = convertView;
            if (inflater != null) {
                if (row == null) row = inflater.inflate(resource, parent, false);
                HashMap<String, String> device = feeders.get(position);
                TextView feederName = row.findViewById(R.id.list_feeder_name);
                feederName.setText(device.get("name"));
                TextView feederMACAddress = row.findViewById(R.id.list_feeder_mac_address);
                feederMACAddress.setText(device.get("address"));
                TextView feederRSSI = row.findViewById(R.id.list_feeder_rssi);
                feederRSSI.setText(device.get("rssi"));
            }
            return row;
        }
    }
}