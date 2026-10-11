package com.pijamaparty.store.catalog.adapter.out.image;

import com.pijamaparty.store.catalog.application.ImageProcessor;
import java.time.Duration;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** The {@link ImageProcessor} of the application: libvips, at most 2 conversions at a time (RNF-21, T-E1-14). */
@Configuration(proxyBeanMethods = false)
class ImageProcessorConfiguration {

    @Bean
    ImageProcessor imageProcessor(@Value("${store.images.vips.bin-dir:}") String binDir,
                                  @Value("${store.images.vips.timeout:30s}") Duration timeout,
                                  @Value("${store.images.conversions.max-concurrent:2}") int maxConcurrent,
                                  @Value("${store.images.conversions.queue-timeout:10s}") Duration queueTimeout) {
        return new ConcurrencyLimitedImageProcessor(new VipsImageProcessor(binDir, timeout), maxConcurrent,
            queueTimeout);
    }
}
