#pragma once

#include "esp_ota_ops.h"
#include "host/ble_hs.h"
#include "host/ble_uuid.h"
#include "services/gap/ble_svc_gap.h"
#include "services/gatt/ble_svc_gatt.h"

#define GATT_SVR_LOG_TAG "GATT_SERVER"
#define REBOOT_DEEP_SLEEP_TIMEOUT 1000
#define GATT_DEVICE_INFO_UUID 0x180A
#define GATT_MANUFACTURER_NAME_UUID 0x2A29
#define GATT_MODEL_NUMBER_UUID 0x2A24

typedef enum
{
    SVR_CHR_OTA_CONTROL_NOP,
    SVR_CHR_OTA_CONTROL_REQUEST,
    SVR_CHR_OTA_CONTROL_REQUEST_ACK,
    SVR_CHR_OTA_CONTROL_REQUEST_NAK,
    SVR_CHR_OTA_CONTROL_DONE,
    SVR_CHR_OTA_CONTROL_DONE_ACK,
    SVR_CHR_OTA_CONTROL_DONE_NAK,
} svr_chr_ota_control_val_t;

// service: VPF Service
// fa1aee3b-79bb-4f8f-b9e6-d083cf934340
static const ble_uuid128_t gatt_svr_svc_vpf_uuid =
    BLE_UUID128_INIT(0x40, 0x43, 0x93, 0xcf, 0x83, 0xd0, 0xe6, 0xb9, 0x8f, 0x4f, 0xbb, 0x79, 0x3b, 0xee, 0x1a, 0xfa);

// characteristic: OTA Control
// fa1aee3b-79bb-4f8f-b9e6-d083cf934341
static const ble_uuid128_t gatt_svr_chr_ota_control_uuid =
    BLE_UUID128_INIT(0x41, 0x43, 0x93, 0xcf, 0x83, 0xd0, 0xe6, 0xb9, 0x8f, 0x4f, 0xbb, 0x79, 0x3b, 0xee, 0x1a, 0xfa);

// characteristic: OTA Data
// fa1aee3b-79bb-4f8f-b9e6-d083cf934342
static const ble_uuid128_t gatt_svr_chr_ota_data_uuid =
    BLE_UUID128_INIT(0x42, 0x43, 0x93, 0xcf, 0x83, 0xd0, 0xe6, 0xb9, 0x8f, 0x4f, 0xbb, 0x79, 0x3b, 0xee, 0x1a, 0xfa);

// characteristic: Settings
// fa1aee3b-79bb-4f8f-b9e6-d083cf934343
static const ble_uuid128_t gatt_svr_chr_state_uuid =
    BLE_UUID128_INIT(0x43, 0x43, 0x93, 0xcf, 0x83, 0xd0, 0xe6, 0xb9, 0x8f, 0x4f, 0xbb, 0x79, 0x3b, 0xee, 0x1a, 0xfa);

// characteristic: Feed command
// fa1aee3b-79bb-4f8f-b9e6-d083cf934344
static const ble_uuid128_t gatt_svr_chr_feed_command_uuid =
    BLE_UUID128_INIT(0x44, 0x43, 0x93, 0xcf, 0x83, 0xd0, 0xe6, 0xb9, 0x8f, 0x4f, 0xbb, 0x79, 0x3b, 0xee, 0x1a, 0xfa);

void gatt_svr_init();
