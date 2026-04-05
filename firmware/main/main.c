#include "sdkconfig.h"
#include "freertos/FreeRTOS.h"
#include "nvs_flash.h"
#include "esp_log.h"
#include "esp_ota_ops.h"
#include "main.h"
#include "i2cdev.h"
#include "ble/server.h"
#include "motor/motor.h"
#include "state/state.h"
#include "scales/scales.h"
#include "clock/clock.h"
#include "command/command.h"
#include "worker/worker.h"

#define MAIN_LOG_TAG "MAIN"

bool run_diagnostics()
{
    // do some diagnostics
    return true;
}

void app_main(void)
{
    esp_err_t err;

    esp_reset_reason_t reason = esp_reset_reason();
    ESP_LOGI(MAIN_LOG_TAG, "Reset Reason : %X", reason);

    // check which partition is running
    const esp_partition_t *partition = esp_ota_get_running_partition();
    switch (partition->address)
    {
    case 0x00010000:
        ESP_LOGI(MAIN_LOG_TAG, "Running partition: factory");
        break;
    case 0x00110000:
        ESP_LOGI(MAIN_LOG_TAG, "Running partition: ota_0");
        break;
    case 0x00210000:
        ESP_LOGI(MAIN_LOG_TAG, "Running partition: ota_1");
        break;

    default:
        ESP_LOGE(MAIN_LOG_TAG, "Running partition: unknown");
        break;
    }

    // check if an OTA has been done, if so run diagnostics
    esp_ota_img_states_t ota_state;
    if (esp_ota_get_state_partition(partition, &ota_state) == ESP_OK)
    {
        if (ota_state == ESP_OTA_IMG_PENDING_VERIFY)
        {
            ESP_LOGI(MAIN_LOG_TAG, "An OTA update has been detected.");
            if (run_diagnostics())
            {
                ESP_LOGI(MAIN_LOG_TAG, "Diagnostics completed successfully! Continuing execution.");
                esp_ota_mark_app_valid_cancel_rollback();
            }
            else
            {
                ESP_LOGE(MAIN_LOG_TAG, "Diagnostics failed! Start rollback to the previous version.");
                esp_ota_mark_app_invalid_rollback_and_reboot();
            }
        }
    }

    // Initialize NVS
    err = nvs_flash_init();
    if (err == ESP_ERR_NVS_NO_FREE_PAGES || err == ESP_ERR_NVS_NEW_VERSION_FOUND)
    {
        ESP_ERROR_CHECK(nvs_flash_erase());
        err = nvs_flash_init();
    }
    ESP_ERROR_CHECK(err);

    // Initialize I2C
    err = i2cdev_init();
    if (err != ESP_OK)
    {
        ESP_LOGE(MAIN_LOG_TAG, "i2cdev_init(): %d (%s)\n", err, esp_err_to_name(err));
    }

    state_init();

    motor_init();

    scales_init();

    clock_init();

    ble_init();

    command_init();

    worker_run();
}