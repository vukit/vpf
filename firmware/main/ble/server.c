#include "gap.h"
#include "gatt.h"
#include "../state/state.h"

#define BLE_SERVER_LOG_TAG "BLE_SERVER"

void ble_init()
{
    ESP_LOGI(BLE_SERVER_LOG_TAG, "BLE server initialization.");
    // initialize nimble stack
    nimble_port_init();

    // register sync and reset callbacks
    ble_hs_cfg.sync_cb = sync_cb;
    ble_hs_cfg.reset_cb = reset_cb;

    // initialize service table
    gatt_svr_init();

    // set device name
    ble_svc_gap_device_name_set(device_name);

    // start host task
    nimble_port_freertos_init(host_task);
}