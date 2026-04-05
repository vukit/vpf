#include <stdlib.h>
#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include "freertos/FreeRTOS.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "nvs_flash.h"
#include "nvs.h"
#include "state.h"
#include "../scales/scales.h"

#define STATE_LOG_TAG "STATE"
#define NVS_NAMESPACE "state"

state_t *state;
SemaphoreHandle_t stateMutex;

void set_zero_state()
{
  state = malloc(sizeof(state_t));
  state->has_changed = false;
  state->date = 0;
  state->scales_zero_value = 0;
  for (int i = 0; i < FEEDINGS_COUNT; i++)
  {
    state->feedings[i].mn = 0;
    state->feedings[i].weight = 0;
    state->feedings[i].hbf = false;
  }
}

void write_state()
{
  esp_err_t err;
  nvs_handle_t handle;
  uint8_t ui8value;

  if (!state->has_changed)
  {
    return;
  }

  ESP_LOGI(STATE_LOG_TAG, "State write.");

  err = nvs_open(NVS_NAMESPACE, NVS_READWRITE, &handle);
  if (err != ESP_OK)
  {
    ESP_LOGE(STATE_LOG_TAG, "Opening NVS handle, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
  }
  else
  {
    err = nvs_set_u32(handle, "date", state->date);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->date, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_set_i32(handle, "szv", state->scales_zero_value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->scales_zero_value, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    // Режим кормления
    err = nvs_set_u16(handle, "f0mn", state->feedings[0].mn);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[0].mn, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_set_i32(handle, "f0w", state->feedings[0].weight);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[0].weight, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    ui8value = (uint8_t)(state->feedings[0].hbf);
    err = nvs_set_u8(handle, "f0hbf", ui8value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[0].hbf, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_set_u16(handle, "f1mn", state->feedings[1].mn);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[1].mn, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_set_i32(handle, "f1w", state->feedings[1].weight);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[1].weight, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    ui8value = (uint8_t)(state->feedings[1].hbf);
    err = nvs_set_u8(handle, "f1hbf", ui8value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[1].hbf, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_set_u16(handle, "f2mn", state->feedings[2].mn);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[2].mn, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_set_i32(handle, "f2w", state->feedings[2].weight);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[2].weight, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    ui8value = (uint8_t)(state->feedings[2].hbf);
    err = nvs_set_u8(handle, "f2hbf", ui8value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed write state->feedings[2].hbf, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }

    err = nvs_commit(handle);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed commit state, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }

    nvs_close(handle);

    state->has_changed = false;
  }
}

void read_state()
{
  esp_err_t err;
  nvs_handle_t handle;
  uint8_t ui8value;

  ESP_LOGI(STATE_LOG_TAG, "State read.");

  err = nvs_open(NVS_NAMESPACE, NVS_READWRITE, &handle);
  if (err != ESP_OK)
  {
    ESP_LOGE(STATE_LOG_TAG, "Opening NVS handle, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
  }
  else
  {
    err = nvs_get_u32(handle, "date", &state->date);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->date, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_i32(handle, "szv", &state->scales_zero_value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->scales_zero_value, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    // Режим кормления
    err = nvs_get_u16(handle, "f0mn", &(state->feedings[0].mn));
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[0].mn, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_i32(handle, "f0w", &(state->feedings[0].weight));
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[0].weight, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_u8(handle, "f0hbf", &ui8value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[0].hbf, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    state->feedings[0].hbf = (bool)ui8value;
    err = nvs_get_u16(handle, "f1mn", &(state->feedings[1].mn));
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[1].mn, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_i32(handle, "f1w", &(state->feedings[1].weight));
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[1].weight, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_u8(handle, "f1hbf", &ui8value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[1].hbf, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    state->feedings[1].hbf = (bool)ui8value;
    err = nvs_get_u16(handle, "f2mn", &(state->feedings[2].mn));
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[2].mn, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_i32(handle, "f2w", &(state->feedings[2].weight));
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[2].weight, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    err = nvs_get_u8(handle, "f2hbf", &ui8value);
    if (err != ESP_OK)
    {
      ESP_LOGE(STATE_LOG_TAG, "Failed read state->feedings[2].hbf, %s: %d (%s)\n", __FUNCTION__, err, esp_err_to_name(err));
    }
    state->feedings[2].hbf = (bool)ui8value;

    nvs_close(handle);
  }
}

void state_init()
{
  ESP_LOGI(STATE_LOG_TAG, "State initialization.");
  set_zero_state();
  read_state();
  if (stateMutex == NULL)
  {
    stateMutex = xSemaphoreCreateMutex();
    if (stateMutex != NULL)
      xSemaphoreGive(stateMutex);
  }
}

void reset_state()
{
  set_zero_state();
  state->has_changed = true;
  write_state();
}

state_t *take_state(bool mode)
{
  if (mode)
  {
    if (xSemaphoreTake(stateMutex, (TickType_t)10) == pdTRUE)
    {
      return state;
    }
    else
    {
      return NULL;
    }
  }
  else
  {
    return state;
  }
}

void give_state()
{
  write_state();
  xSemaphoreGive(stateMutex);
}
