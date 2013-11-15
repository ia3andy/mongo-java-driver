/*
 * Copyright (c) 2008 - 2013 10gen, Inc. <http://10gen.com>
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.mongodb.operation;

import org.mongodb.Codec;
import org.mongodb.CommandResult;
import org.mongodb.Decoder;
import org.mongodb.Document;
import org.mongodb.MongoCursor;
import org.mongodb.MongoNamespace;
import org.mongodb.ReadPreference;
import org.mongodb.codecs.DocumentCodec;
import org.mongodb.codecs.PrimitiveCodecs;
import org.mongodb.connection.BufferProvider;
import org.mongodb.protocol.CommandProtocol;
import org.mongodb.session.ServerConnectionProvider;
import org.mongodb.session.ServerConnectionProviderOptions;
import org.mongodb.session.Session;

import java.util.List;

import static org.mongodb.operation.CommandDocuments.createMapReduce;

/**
 * Operation that runs a Map Reduce against a MongoDB instance.  This operation only supports "inline" results, i.e. the results will be
 * returned as a result of running this operation.
 * <p/>
 * To run a map reduce operation into a given collection, use {@code MapReduceToCollectionOperation}.
 *
 * @see <a href="http://docs.mongodb.org/manual/core/map-reduce/">Map-Reduce</a>
 */
public class MapReduceWithInlineResultsOperation<T> extends BaseOperation<MongoCursor<T>> {
    private final Document command;
    private final MongoNamespace namespace;
    private final ReadPreference readPreference;
    private final MapReduceCommandResultCodec<T> mapReduceResultDecoder;
    private final Codec<Document> commandCodec = new DocumentCodec();

    /**
     * Construct a MapReduceOperation with all the criteria it needs to execute
     *
     * @param namespace      the database and collection to perform the map reduce on
     * @param mapReduce      the bean containing all the details of the Map Reduce operation to perform
     * @param decoder        the decoder to use for decoding the Documents in the results of the map-reduce operation
     * @param readPreference the read preference suggesting which server to run the command on
     * @param bufferProvider the BufferProvider to use when reading or writing to the network
     * @param session        the current Session, which will give access to a connection to the MongoDB instance
     * @param closeSession   true if the session should be closed at the end of the execute method
     */
    public MapReduceWithInlineResultsOperation(final MongoNamespace namespace, final MapReduce mapReduce,
                                               final Decoder<T> decoder, final ReadPreference readPreference,
                                               final BufferProvider bufferProvider, final Session session, final boolean closeSession) {
        super(bufferProvider, session, closeSession);
        this.namespace = namespace;
        this.readPreference = readPreference;
        this.mapReduceResultDecoder = new MapReduceCommandResultCodec<T>(PrimitiveCodecs.createDefault(), decoder);
        this.command = createMapReduce(namespace.getCollectionName(), mapReduce);
    }

    /**
     * Executing this will return a cursor with your results in.
     *
     * @return a MongoCursor that can be iterated over to find all the results of the Map Reduce operation.
     */
    @Override
    @SuppressWarnings("unchecked")
    public MongoCursor<T> execute() {
        ServerConnectionProvider provider = getSession().createServerConnectionProvider(getServerConnectionProviderOptions());
        CommandResult commandResult = new CommandProtocol(namespace.getDatabaseName(), command, commandCodec, mapReduceResultDecoder,
                                                          getBufferProvider(), provider.getServerDescription(), provider.getConnection(),
                                                          isCloseSession())
                                          .execute();

        return new InlineMongoCursor<T>(commandResult, (List<T>) commandResult.getResponse().get("results"));
    }

    private ServerConnectionProviderOptions getServerConnectionProviderOptions() {
        return new ServerConnectionProviderOptions(true, new ReadPreferenceServerSelector(readPreference));
    }
}
