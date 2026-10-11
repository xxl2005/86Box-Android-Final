/* Android-only optional cycle budgeting for 86Box's ORIGINAL CPU core.
 * No guest CPU state, JIT instructions, timers or architectural MHz are modified.
 * No background workers; AUTO uses published 86Box speed samples (typically 1/s).
 * We clamp budgets to the nominal CPU profile to prevent accidental overclocking.
 */
#include "android_cpu_cycles.h"
#include <stdatomic.h>
#include <stdio.h>
#include <stdint.h>

static _Atomic int mode_setting=0;
static _Atomic int fixed_percent=100;
static _Atomic int custom_kcps=50000;
static _Atomic int auto_permille=1000;
static _Atomic int bad_samples=0;
static _Atomic int good_samples=0;
static _Atomic int last_speed=-1;
static _Atomic int last_nominal=0;
static _Atomic int last_budget=0;
static _Atomic int last_quantum_us=1000;

void android_cpu_cycles_set_mode(int mode) {
    if (mode<0 || mode>4) mode=0;
    atomic_store(&mode_setting,mode);
    atomic_store(&auto_permille,1000);
    atomic_store(&bad_samples,0);
    atomic_store(&good_samples,0);
}
void android_cpu_cycles_set_fixed_percent(int percent) {
    if(percent<25) percent=25;
    if(percent>100) percent=100;
    atomic_store(&fixed_percent,percent);
}
void android_cpu_cycles_set_custom_kcps(int kcps) {
    if(kcps<1000) kcps=1000;
    if(kcps>500000) kcps=500000;
    atomic_store(&custom_kcps,kcps);
}
void android_cpu_cycles_observe_speed(int speed) {
    atomic_store(&last_speed,speed);
    if(atomic_load(&mode_setting)!=2 || speed<0) return;
    int value=atomic_load(&auto_permille);
    if(speed<94) {
        atomic_store(&good_samples,0);
        if(atomic_fetch_add(&bad_samples,1)+1>=3) {
            atomic_store(&bad_samples,0);
            value-=50; /* reduce a maximum 5 percentage points per 3 bad seconds */
            if(value<500) value=500;
            atomic_store(&auto_permille,value);
        }
    } else if(speed>=99 && speed<=102) {
        atomic_store(&bad_samples,0);
        if(atomic_fetch_add(&good_samples,1)+1>=4) {
            atomic_store(&good_samples,0);
            value+=20; /* slow recovery instead of oscillating every frame */
            if(value>1000) value=1000;
            atomic_store(&auto_permille,value);
        }
    } else {
        atomic_store(&bad_samples,0);
        atomic_store(&good_samples,0);
    }
}
int32_t android_cpu_cycles_budget(int32_t nominal,int quantum_us) {
    if(nominal<=0) return nominal;
    if(quantum_us!=1000 && quantum_us!=10000) quantum_us=1000;
    int64_t budget=nominal;
    int mode=atomic_load(&mode_setting);
    switch(mode) {
        case 1: budget=((int64_t)nominal*atomic_load(&fixed_percent)+50)/100;break;
        case 2: budget=((int64_t)nominal*atomic_load(&auto_permille)+500)/1000;break;
        case 4: budget=((int64_t)atomic_load(&custom_kcps)*quantum_us+500)/1000;break;
        default: break; /* Native/Max: exactly nominal 86Box budget */
    }
    if(budget<1) budget=1;
    if(budget>nominal) budget=nominal;
    atomic_store(&last_nominal,nominal);
    atomic_store(&last_budget,(int)budget);
    atomic_store(&last_quantum_us,quantum_us);
    return (int32_t)budget;
}
void android_cpu_cycles_get_status(char *out,size_t capacity) {
    if(!out || !capacity) return;
    static const char *names[]={"Original 86Box","Fixed","Auto","Max","Custom"};
    int mode=atomic_load(&mode_setting),n=atomic_load(&last_nominal);
    int run=atomic_load(&last_budget),us=atomic_load(&last_quantum_us);
    if(mode<0 || mode>4) mode=0;
    /* rspeed-based budget is a cycle cap, not a benchmark of achieved guest MHz. */
    snprintf(out,capacity,
       "CPU core=original 86Box; cycles=%s; fixed=%d%%; custom=%d kcycles/s; "
       "auto_scale=%d.%d%%; budget=%d/%d cycles per %d us; "
       "budget_cap=%.2f/%.2f MHz; observed_speed=%d%%; no extra guest CPUs",
       names[mode],atomic_load(&fixed_percent),atomic_load(&custom_kcps),
       atomic_load(&auto_permille)/10,atomic_load(&auto_permille)%10,
       run,n,us, us>0?(double)run/(double)us:0.0,
       us>0?(double)n/(double)us:0.0,atomic_load(&last_speed));
}
