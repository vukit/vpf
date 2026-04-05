#pragma once

#define COMMAND_OK 0x00
#define COMMAND_ERR_INSUFFICIENT_RES 0x01
#define COMMAND_ERR_INVALID_JSON 0x02
#define COMMAND_ERR_FEEDER_BUSY 0x03
#define COMMAND_FEEDING_PROGRESS 0x04
#define COMMAND_FEEDING_STOP 0x05

typedef struct
{
    char *json;
    int rc;
} json_state_t;

void command_init();
int32_t feed(int32_t weight, void callback(uint8_t code, int32_t weight));
json_state_t get_json_state();
int save_state(const void *state);
