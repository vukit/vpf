#include "esp_log.h"
#include <string.h>
#include <time.h>
#include <sys/time.h>
#include <ds3231.h>
#include "clock.h"

#define CLOCK_LOG_TAG "CLOCK"
#define DS321_LOW_LIMIT_TEMPERATURE -40

i2c_dev_t clock_dev;
struct tm clock_time;
float temperature = DS321_LOW_LIMIT_TEMPERATURE;

void set_localtime();
void read_temperature(void *temperature);

void clock_init()
{
    memset(&clock_dev, 0, sizeof(i2c_dev_t));

    esp_err_t r = ds3231_init_desc(&clock_dev, 0, CONFIG_DS3231_SDA_OUTPUT_PORT, CONFIG_DS3231_SCL_INPUT_PORT);

    if (r != ESP_OK)
    {
        ESP_LOGE(CLOCK_LOG_TAG, "%s: %d (%s)\n", __FUNCTION__, r, esp_err_to_name(r));
    }

    set_localtime();

    clock_time = get_time();
    ESP_LOGI(
        CLOCK_LOG_TAG,
        "Clock initialization: %04d-%02d-%02d %02d:%02d:%02d",
        clock_time.tm_year + 1900,
        clock_time.tm_mon + 1,
        clock_time.tm_mday,
        clock_time.tm_hour,
        clock_time.tm_min,
        clock_time.tm_sec);
}

void set_time(int year, int month, int day, int hour, int minute, int second)
{
    struct tm new_time = {
        .tm_year = year - 1900, // since 1900 (2018 - 1900)
        .tm_mon = month - 1,    // 0-based
        .tm_mday = day,
        .tm_hour = hour,
        .tm_min = minute,
        .tm_sec = second};

    esp_err_t r = ds3231_set_time(&clock_dev, &new_time);
    if (r != ESP_OK)
    {
        ESP_LOGE(CLOCK_LOG_TAG, "%s: %d (%s)\n", __FUNCTION__, r, esp_err_to_name(r));
    }

    set_localtime();
}

struct tm get_time()
{
    time_t rawtime;

    time(&rawtime);

    return *localtime(&rawtime);
}

void set_localtime()
{
    esp_err_t r = ds3231_get_time(&clock_dev, &clock_time);
    if (r != ESP_OK)
    {
        ESP_LOGE(CLOCK_LOG_TAG, "%s: %d (%s)\n", __FUNCTION__, r, esp_err_to_name(r));
    }

    const struct timeval tv = {mktime(&clock_time), 0};
    settimeofday(&tv, 0);

    xTaskCreate(read_temperature, "read_temperature", 1024, &temperature, tskIDLE_PRIORITY, NULL);
}

void read_temperature(void *temperature)
{

    for (;;)
    {
        esp_err_t r = ds3231_get_temp_float(&clock_dev, temperature);
        if (r != ESP_OK)
        {
            ESP_LOGE(CLOCK_LOG_TAG, "%s: %d (%s)\n", __FUNCTION__, r, esp_err_to_name(r));
            *(float *)temperature = DS321_LOW_LIMIT_TEMPERATURE;
        }
        vTaskDelay(pdMS_TO_TICKS(5000));
    }
}

float get_temperature()
{
    while (temperature == DS321_LOW_LIMIT_TEMPERATURE)
    {
        vTaskDelay(pdMS_TO_TICKS(500));
    }

    return temperature;
}
