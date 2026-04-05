#include <time.h>
#include "freertos/FreeRTOS.h"
#include "freertos/task.h"
#include "esp_log.h"
#include "../state/state.h"
#include "../clock/clock.h"
#include "../command/command.h"

#define WORKER_LOG_TAG "WORKER"

void worker_run()
{
    ESP_LOGI(WORKER_LOG_TAG, "Worker is working.");

    state_t *state;
    struct tm ts;
    uint32_t date;

    for (;;)
    {
        state = take_state(GET_STATE_FOR_WRITING);
        if (state == NULL)
        {
            continue;
        }

        ts = get_time();
        ts.tm_year += 1900;
        ts.tm_mon += 1;

        ESP_LOGI(WORKER_LOG_TAG, "Checking feeding at %04d-%02d-%02d %02d:%02d:%02d", ts.tm_year, ts.tm_mon, ts.tm_mday, ts.tm_hour, ts.tm_min, ts.tm_sec);

        date = ts.tm_year * 10000 + ts.tm_mon * 100 + ts.tm_mday;
        if (state->date != date)
        {
            for (int i = 0; i < FEEDINGS_COUNT; i++)
            {
                state->feedings[i].hbf = false;
            }
            state->date = date;
            state->has_changed = true;
        }

        for (int i = 0; i < FEEDINGS_COUNT; i++)
        {
            ts = get_time();
            if (
                !state->feedings[i].hbf &&
                state->feedings[i].mn <= 60 * ts.tm_hour + ts.tm_min &&
                state->feedings[i].weight > 0)
            {
                ESP_LOGI(WORKER_LOG_TAG, "Feeding #%d.", i + 1);
                if (feed(state->feedings[i].weight, NULL) == COMMAND_OK)
                {
                    state->feedings[i].hbf = true;
                    state->has_changed = true;
                } else {
                    ESP_LOGI(WORKER_LOG_TAG, "Feeder busy");
                }
            }
        }

        give_state();

        ts = get_time();
        vTaskDelay(pdMS_TO_TICKS(60000 - ts.tm_sec * 1000));
    }
}
