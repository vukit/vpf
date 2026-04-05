#include <stdio.h>
#include <stdbool.h>
#include <math.h>
#include "sdkconfig.h"
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "hx711.h"
#include "../state/state.h"
#include "../clock/clock.h"

#define SCALES_LOG_TAG "SCALES"
#define UNDEFINED_SCALES_VALUE -1

hx711_t scales_dev = {
    .dout = CONFIG_HX711_SERIAL_DATA_OUTPUT_PORT,
    .pd_sck = CONFIG_HX711_SERIAL_CLOCK_INPUT_PORT,
    .gain = HX711_GAIN_A_64};

float k = 0.00787682371364;
int32_t scales_value = UNDEFINED_SCALES_VALUE;
int32_t scales_zero_value = 0;

void read_scales(void *scales_value);

void scales_init()
{
    ESP_LOGI(SCALES_LOG_TAG, "Scales initialization.");

    state_t *state = take_state(GET_STATE_FOR_READING);
    scales_zero_value = state->scales_zero_value;

    esp_err_t r = hx711_init(&scales_dev);
    if (r != ESP_OK)
    {
        ESP_LOGE(SCALES_LOG_TAG, "%d (%s)\n", r, esp_err_to_name(r));
    }

    xTaskCreate(read_scales, "read_scales", 2048, &scales_value, tskIDLE_PRIORITY, NULL);
}

void read_scales(void *scales_value)
{
    esp_err_t r;
    int32_t v, s;

    for (;;)
    {
        s = 0;
        for (size_t i = 0; i < CONFIG_HX711_AVG_TIMES; i++)
        {
            r = hx711_wait(&scales_dev, 200);
            if (r != ESP_OK)
            {
                ESP_LOGE(SCALES_LOG_TAG, "%s: %d (%s)\n", __FUNCTION__, r, esp_err_to_name(r));
                *(int32_t *)scales_value = UNDEFINED_SCALES_VALUE;
                continue;
            }
            r = hx711_read_data(&scales_dev, &v);
            if (r != ESP_OK)
            {
                ESP_LOGE(SCALES_LOG_TAG, "%s: %d (%s)\n", __FUNCTION__, r, esp_err_to_name(r));
                *(int32_t *)scales_value = UNDEFINED_SCALES_VALUE;
                continue;
            }
            s += v;
        }
        *(int32_t *)scales_value = s / (int32_t)CONFIG_HX711_AVG_TIMES;
    }
}

int32_t get_scales_weight()
{
    while (scales_value == UNDEFINED_SCALES_VALUE)
    {
        vTaskDelay(pdMS_TO_TICKS(500));
    }

    return (int32_t)round(k * (scales_value - scales_zero_value));
}

int32_t set_scales_zero()
{
    while (scales_value == UNDEFINED_SCALES_VALUE)
    {
        vTaskDelay(pdMS_TO_TICKS(500));
    }

    scales_zero_value = scales_value;

    return scales_zero_value;
}