package io.github.spencerpark.ijava;

import io.github.spencerpark.jupyter.client.api.KernelInfo;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.Timeout;

import java.util.concurrent.TimeUnit;

import static org.hamcrest.CoreMatchers.is;
import static org.junit.Assert.assertThat;

public class JavaKernelTest {
    @Rule
    public Timeout timeout = new Timeout(10, TimeUnit.SECONDS);

    @Rule
    public IJavaClient client = new IJavaClient();

    @Test
    public void testKernelInfo() throws Exception {
        KernelInfo info = this.client.get().getKernelInfo();
        assertThat(info.getImplementationName(), is("IJava"));
        assertThat(info.getImplementationVersion(), is(IJavaBuildInfo.VERSION));
        assertThat(info.getLangInfo().getName(), is("Java"));
    }
}
