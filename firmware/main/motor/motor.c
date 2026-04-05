#include <stdio.h>
#include "sdkconfig.h"
#include "freertos/FreeRTOS.h"
#include "driver/gpio.h"
#include "esp_log.h"
#include "motor.h"

#define MOTOR_LOG_TAG "MOTOR"

void motor_init()
{
    ESP_LOGI(MOTOR_LOG_TAG, "Motor initialization.");
    gpio_reset_pin(CONFIG_MOTOR_DRIVER_PORT);
    gpio_pulldown_en(CONFIG_MOTOR_DRIVER_PORT);
    gpio_set_direction(CONFIG_MOTOR_DRIVER_PORT, GPIO_MODE_OUTPUT);
    gpio_set_level(CONFIG_MOTOR_DRIVER_PORT, 0);
}

void motor_start()
{
    ESP_LOGI(MOTOR_LOG_TAG, "Motor start.");
    gpio_set_level(CONFIG_MOTOR_DRIVER_PORT, 1);
}

void motor_stop()
{
    ESP_LOGI(MOTOR_LOG_TAG, "Motor stop.");
    gpio_set_level(CONFIG_MOTOR_DRIVER_PORT, 0);
}
