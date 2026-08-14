package com.example.devicemanagement.app;

import static org.junit.Assert.assertArrayEquals;
import static org.junit.Assert.assertEquals;

import com.example.devicemanagement.action.SensitiveActionController;
import com.example.devicemanagement.trigger.Trigger;
import java.lang.reflect.Method;
import java.lang.reflect.Modifier;
import java.util.Arrays;
import org.junit.Test;

public final class PublicControllerApiJavaTest {
    @Test
    public void appVisibleControllerExposesSubmitOnly() {
        Method[] publicMethods = Arrays.stream(SensitiveActionController.class.getDeclaredMethods())
                .filter(method -> Modifier.isPublic(method.getModifiers()))
                .toArray(Method[]::new);

        assertEquals(1, publicMethods.length);
        assertEquals("submit", publicMethods[0].getName());
        assertArrayEquals(new Class<?>[] {Trigger.class}, publicMethods[0].getParameterTypes());
    }
}
