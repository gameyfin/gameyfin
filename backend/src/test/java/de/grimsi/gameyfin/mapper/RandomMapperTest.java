package de.grimsi.gameyfin.mapper;

import com.google.protobuf.Message;
import org.jeasy.random.EasyRandom;
import org.jeasy.random.EasyRandomParameters;
import org.springframework.core.GenericTypeResolver;

import java.util.List;

public class RandomMapperTest<Input extends Message, Output> {

    private static final int DEFAULT_COUNT = 5;
    private final EasyRandom easyRandom = new EasyRandom();

    private final Class<Input> inputClass;
    private final Class<Output> outputClass;

    @SuppressWarnings("unchecked")
    public RandomMapperTest() {
        Class<?>[] typeArguments = GenericTypeResolver.resolveTypeArguments(getClass(), RandomMapperTest.class);
        assert typeArguments != null;
        inputClass = (Class<Input>) typeArguments[0];
        outputClass = (Class<Output>) typeArguments[1];
    }

    protected Input generateRandomInput() {
        return easyRandom.nextObject(inputClass);
    }

    protected List<Input> generateRandomInputs() {
        return easyRandom.objects(inputClass, DEFAULT_COUNT).toList();
    }

    protected List<Input> generateRandomInputs(int count) {
        return easyRandom.objects(inputClass, count).toList();
    }

    protected Output generateRandomOutput() {
        return easyRandom.nextObject(outputClass);
    }

    protected List<Output> generateRandomOutputs() {
        return easyRandom.objects(outputClass, DEFAULT_COUNT).toList();
    }

    protected List<Output> generateRandomOutputs(int count) {
        return easyRandom.objects(outputClass, count).toList();
    }
}
