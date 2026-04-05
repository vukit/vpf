#pragma once

#define FEEDER_NAME_LENGTH 40
#define FEEDINGS_COUNT 3

#define GET_STATE_FOR_WRITING true
#define GET_STATE_FOR_READING false

typedef struct
{
  uint16_t mn;    // порядковый номер минуты в сутках, nvs key name - fNnm, где N - номер кормления
  int32_t weight; // вес порции корма, nvs key name - fNw, где N - номер кормления
  bool hbf;       // было ли кормление?, nvs key name - fNhbf, где N - номер кормления
} feeding_t;

typedef struct
{
  feeding_t feedings[FEEDINGS_COUNT];
  uint32_t date;             // текущая дата кормления, nvs key name - date
  int32_t scales_zero_value; // нулевое значение весов, nvs key name - szv
  bool has_changed;
} state_t;

void state_init();
state_t *take_state(bool mode);
void give_state();
void reset_state();
