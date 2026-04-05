#pragma once

void clock_init();
void set_time(int year, int month, int day, int hour, int minute, int second);
float get_temperature();
struct tm get_time();
