/* CPU Cycles independent test: original 86Box nominal budget is sacred. */
#include <assert.h>
#include <pthread.h>
#include <stdint.h>
#include <stdio.h>
#include <string.h>
#include "../../../src/unix/android_cpu_cycles.h"
static void *worker(void *v) {
    (void)v;
    for (int i=0;i<40000;++i) {
        int32_t r=android_cpu_cycles_budget(200000,1000);
        assert(r>=1 && r<=200000);
    }
    return NULL;
}
int main(void) {
    char state[512];
    /* Exact unmodified original must be the default and recoverable. */
    assert(android_cpu_cycles_budget(200000,1000)==200000);
    android_cpu_cycles_set_fixed_percent(50);
    android_cpu_cycles_set_mode(1);
    assert(android_cpu_cycles_budget(200000,1000)==100000);
    assert(android_cpu_cycles_budget(2000000,10000)==1000000);
    android_cpu_cycles_set_fixed_percent(0);
    assert(android_cpu_cycles_budget(200000,1000)==50000);
    android_cpu_cycles_set_fixed_percent(110);
    assert(android_cpu_cycles_budget(200000,1000)==200000);
    android_cpu_cycles_set_mode(4);
    android_cpu_cycles_set_custom_kcps(50000);
    assert(android_cpu_cycles_budget(200000,1000)==50000);
    assert(android_cpu_cycles_budget(2000000,10000)==500000);
    android_cpu_cycles_set_custom_kcps(500000);
    assert(android_cpu_cycles_budget(200000,1000)==200000);
    android_cpu_cycles_set_mode(3);
    assert(android_cpu_cycles_budget(200000,1000)==200000);
    android_cpu_cycles_set_mode(2);
    for(int i=0;i<3;++i) android_cpu_cycles_observe_speed(51);
    assert(android_cpu_cycles_budget(200000,1000)==190000);
    for(int i=0;i<4;++i) android_cpu_cycles_observe_speed(100);
    assert(android_cpu_cycles_budget(200000,1000)==194000);
    android_cpu_cycles_set_mode(0);
    assert(android_cpu_cycles_budget(200000,1000)==200000);
    android_cpu_cycles_get_status(state,sizeof state);
    assert(strstr(state,"original 86Box")!=NULL);
    assert(strstr(state,"budget_cap=200.00/200.00 MHz")!=NULL);
    assert(android_cpu_cycles_budget(0,1000)==0);
    pthread_t threads[4];
    for(int j=0;j<4;++j) assert(pthread_create(&threads[j],NULL,worker,NULL)==0);
    for(int j=0;j<4;++j) assert(pthread_join(threads[j],NULL)==0);
    puts("PASS: 86Box original CPU cycle policy and concurrent budget reads");
    return 0;
}
