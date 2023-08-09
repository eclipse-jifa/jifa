/********************************************************************************
 * Copyright (c) 2022 Contributors to the Eclipse Foundation
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * This program and the accompanying materials are made available under the
 * terms of the Eclipse Public License 2.0 which is available at
 * http://www.eclipse.org/legal/epl-2.0
 *
 * SPDX-License-Identifier: EPL-2.0
 ********************************************************************************/

package org.eclipse.jifa.tda.parser;

import com.esotericsoftware.kryo.Kryo;
import com.esotericsoftware.kryo.io.Input;
import com.esotericsoftware.kryo.io.Output;
import lombok.extern.slf4j.Slf4j;
import org.eclipse.jifa.analysis.listener.ProgressListener;
import org.eclipse.jifa.tda.model.Snapshot;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import java.nio.file.Paths;

@Slf4j
public class SerDesParser implements Parser {

    private static final ThreadLocal<Kryo> KRYO;

    static {
        KRYO = ThreadLocal.withInitial(() -> {
            Kryo kryo = new Kryo();
            kryo.setRegistrationRequired(false);
            return kryo;
        });
    }

    private final Parser parser;

    public SerDesParser(Parser parser) {
        this.parser = parser;
    }

    private Path storage(Path from) {
        return Paths.get(from.toFile().getAbsoluteFile() + ".kryo");
    }

    @Override
    public Snapshot parse(Path path, ProgressListener listener) {
        // TODO: multi-threads support
        Path storage = storage(path);
        if (storage.toFile().exists()) {
            try {
                listener.beginTask("Deserializing thread dump snapshot", 100);
                Snapshot snapshot = deserialize(storage);
                listener.worked(100);
                return snapshot;
            } catch (Throwable t) {
                log.error("Deserialize thread dump snapshot failed", t);
                listener.sendUserMessage(ProgressListener.Level.WARNING, "Deserialize thread dump snapshot failed", t);
                listener.reset();
            }
        }

        listener.beginTask(null, 5);
        Snapshot snapshot = parser.parse(path, listener);
        try {
            serialize(snapshot, storage);
            listener.worked(5);
        } catch (Throwable t) {
            log.error("Serialize snapshot failed", t);
        }
        return snapshot;
    }

    private void serialize(Snapshot snapshot, Path path) throws FileNotFoundException {
        Kryo kryo = KRYO.get();
        try (Output out = new Output(new FileOutputStream(path.toFile()))) {
            kryo.writeObject(out, snapshot);
        }
    }

    private Snapshot deserialize(Path path) throws IOException {
        Kryo kryo = KRYO.get();
        try (Input input = new Input(new FileInputStream(path.toFile()))) {
            return kryo.readObject(input, Snapshot.class);
        }
    }
}
