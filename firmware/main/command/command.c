#include <stdint.h>
#include <stdbool.h>
#include <string.h>
#include <time.h>
#include <sys/time.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "freertos/semphr.h"
#include "esp_log.h"
#include "cJSON.h"
#include "../main.h"
#include "../state/state.h"
#include "../clock/clock.h"
#include "../scales/scales.h"
#include "../motor/motor.h"
#include "command.h"

#define COMMAND_LOG_TAG "COMMAND"

#define FEED_LOOP_DELAY 500                                // 500 мсек
#define FEED_TIMEOUT_COUNTER (50 * 1000) / FEED_LOOP_DELAY // 50 cek
#define FEED_CALLBACK_COUNTER (5 * 1000) / FEED_LOOP_DELAY // 5 сек

SemaphoreHandle_t feedMutex;

void command_init()
{
  ESP_LOGI(COMMAND_LOG_TAG, "Command initialization.");
  if (feedMutex == NULL)
  {
    feedMutex = xSemaphoreCreateMutex();
    if (feedMutex != NULL)
      xSemaphoreGive(feedMutex);
  }
}

int32_t feed(int32_t weight, void callback(uint8_t code, int32_t weight))
{
  uint8_t timeout = 0, callback_time = 0;
  int32_t prev_weight = 0, current_weight = 0, delta_weight = 0, callback_weight = 0;

  if (xSemaphoreTake(feedMutex, (TickType_t)10) != pdTRUE)
  {
    if (callback != NULL)
    {
      callback(COMMAND_ERR_FEEDER_BUSY, 0);
    }
    return COMMAND_ERR_FEEDER_BUSY;
  }

  prev_weight = get_scales_weight();
  motor_start();
  for (;;)
  {
    vTaskDelay(pdMS_TO_TICKS(FEED_LOOP_DELAY));

    timeout++;
    if (timeout >= FEED_TIMEOUT_COUNTER)
    {
      break;
    }

    current_weight = get_scales_weight();
    delta_weight = prev_weight - current_weight > 0 ? prev_weight - current_weight : 0;
    prev_weight = current_weight;
    weight -= delta_weight;
    callback_weight += delta_weight;

    ESP_LOGI(COMMAND_LOG_TAG, "%s: delta_weight = %ld, weight = %ld", __FUNCTION__, delta_weight, weight);

    callback_time++;
    if (callback != NULL && callback_time == FEED_CALLBACK_COUNTER)
    {
      callback(COMMAND_FEEDING_PROGRESS, callback_weight);
      callback_time = 0;
    }

    if (weight <= 0)
    {
      break;
    }
  }

  motor_stop();

  if (callback != NULL)
  {
    vTaskDelay(pdMS_TO_TICKS(1500));
    callback(COMMAND_FEEDING_STOP, callback_weight);
  }

  xSemaphoreGive(feedMutex);

  return COMMAND_OK;
}

json_state_t get_json_state()
{
  json_state_t result;

  result.rc = COMMAND_OK;

  state_t *state = take_state(GET_STATE_FOR_READING);

  cJSON *stateObject = cJSON_CreateObject();
  if (stateObject == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    return result;
  }

  time_t ts;
  time(&ts);
  if (cJSON_AddNumberToObject(stateObject, "timestamp", ts) == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    goto end;
  }

  if (cJSON_AddNumberToObject(stateObject, "weight", get_scales_weight()) == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    goto end;
  }

  if (cJSON_AddNumberToObject(stateObject, "temperature", get_temperature()) == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    goto end;
  }

  cJSON *feedings = cJSON_AddArrayToObject(stateObject, "feedings");
  if (feedings == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    goto end;
  }

  for (int i = 0; i < FEEDINGS_COUNT; i++)
  {
    cJSON *feeding = cJSON_CreateObject();

    if (cJSON_AddNumberToObject(feeding, "mn", state->feedings[i].mn) == NULL)
    {
      result.rc = COMMAND_ERR_INSUFFICIENT_RES;
      goto end;
    }

    if (cJSON_AddNumberToObject(feeding, "weight", state->feedings[i].weight) == NULL)
    {
      result.rc = COMMAND_ERR_INSUFFICIENT_RES;
      goto end;
    }

    cJSON_AddItemToArray(feedings, feeding);
  }

  if (cJSON_AddStringToObject(stateObject, "model", MODEL_NUMBER_STR) == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    goto end;
  }

  result.json = cJSON_PrintUnformatted(stateObject);
  if (result.json == NULL)
  {
    result.rc = COMMAND_ERR_INSUFFICIENT_RES;
    goto end;
  }

end:
  cJSON_Delete(stateObject);
  if (result.rc == COMMAND_OK)
  {
    ESP_LOGI(COMMAND_LOG_TAG, "State: %s", result.json);
  }
  else
  {
    ESP_LOGE(COMMAND_LOG_TAG, "Get state error: %d", result.rc);
  }

  return result;
}

int save_state(const void *state)
{
  int rc = COMMAND_OK;

  state_t *feeder_state = take_state(GET_STATE_FOR_WRITING);
  if (feeder_state == NULL)
  {
    return COMMAND_ERR_FEEDER_BUSY;
  }

  cJSON *state_json = cJSON_Parse((const char *)state);
  if (state_json == NULL)
  {
    const char *error_ptr = cJSON_GetErrorPtr();
    if (error_ptr != NULL)
    {
      ESP_LOGE(COMMAND_LOG_TAG, "%s: Error before: %s", __FUNCTION__, error_ptr);
    }
    give_state();
    return COMMAND_ERR_INVALID_JSON;
  }

  const cJSON *timestamp = cJSON_GetObjectItemCaseSensitive(state_json, "timestamp");
  if (cJSON_IsNumber(timestamp))
  {
    struct tm tm = *localtime(&(time_t){timestamp->valueint});
    set_time(tm.tm_year + 1900, tm.tm_mon + 1, tm.tm_mday, tm.tm_hour, tm.tm_min, tm.tm_sec);
  }
  else
  {
    rc = COMMAND_ERR_INVALID_JSON;
  }

  const cJSON *set_scales_zero_flag = cJSON_GetObjectItemCaseSensitive(state_json, "set_scales_zero");
  if (cJSON_IsBool(set_scales_zero_flag))
  {
    if (set_scales_zero_flag->valueint)
    {
      feeder_state->scales_zero_value = set_scales_zero();
    }
  }
  else
  {
    rc = COMMAND_ERR_INVALID_JSON;
  }

  struct tm ts = get_time();
  uint8_t feeding_index = 0;
  const cJSON *feedings = cJSON_GetObjectItemCaseSensitive(state_json, "feedings");
  const cJSON *feeding;
  cJSON_ArrayForEach(feeding, feedings)
  {
    cJSON *mn = cJSON_GetObjectItemCaseSensitive(feeding, "mn");
    cJSON *weight = cJSON_GetObjectItemCaseSensitive(feeding, "weight");
    if (!cJSON_IsNumber(mn) || !cJSON_IsNumber(weight))
    {
      rc = COMMAND_ERR_INVALID_JSON;
      feeding_index++;
      continue;
    }
    feeder_state->feedings[feeding_index].mn = mn->valueint;
    feeder_state->feedings[feeding_index].weight = weight->valueint;
    if (mn->valueint < 60 * ts.tm_hour + ts.tm_min)
    {
      feeder_state->feedings[feeding_index].hbf = true;
    }
    else
    {
      feeder_state->feedings[feeding_index].hbf = false;
    }
    feeding_index++;
  }

  if (rc == COMMAND_OK)
  {
    feeder_state->has_changed = true;
  }

  give_state();
  cJSON_Delete(state_json);

  return rc;
}
