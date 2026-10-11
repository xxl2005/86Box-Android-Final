/* Android Stage 1 networking stub: use 86Box's null provider when SLiRP is selected. */
#include <stdio.h>
#include <stdint.h>
#include <86box/device.h>
#include <86box/thread.h>
#include <86box/timer.h>
#include <86box/network.h>
#include <86box/plat_unused.h>

static void *
net_slirp_android_init(UNUSED(const netcard_t *card), UNUSED(const uint8_t *mac_addr),
                       UNUSED(void *priv), char *netdrv_errbuf)
{
    if (netdrv_errbuf != NULL)
        snprintf(netdrv_errbuf, NET_DRV_ERRBUF_SIZE,
                 "SLiRP networking is not enabled in the Android Stage 1 build");
    return NULL;
}

const netdrv_t net_slirp_drv = {
    .notify_in = NULL,
    .init      = &net_slirp_android_init,
    .close     = NULL,
    .priv      = NULL
};
