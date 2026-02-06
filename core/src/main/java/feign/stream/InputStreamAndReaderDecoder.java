/*
 * ====================================================================
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *   http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an
 * "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
 * KIND, either express or implied.  See the License for the
 * specific language governing permissions and limitations
 * under the License.
 * ====================================================================
 *
 * This software consists of voluntary contributions made by many
 * individuals on behalf of the Apache Software Foundation.  For more
 * information on the Apache Software Foundation, please see
 * <http://www.apache.org/>.
 *
 */
package feign.stream;

import java.io.IOException;
import java.io.InputStream;
import java.io.Reader;
import java.lang.reflect.Type;

import feign.FeignException;
import feign.Response;
import feign.codec.DecodeException;
import feign.codec.Decoder;

public class InputStreamAndReaderDecoder implements Decoder{
	private final Decoder delegateDecoder;
	
	public InputStreamAndReaderDecoder(Decoder delegate) {
		this.delegateDecoder = delegate;
	}

	@Override
	public Object decode(Response response, Type type) throws IOException, DecodeException, FeignException {

		if (InputStream.class.equals(type))
			return response.body().asInputStream();
		
		if (Reader.class.equals(type))
			return response.body().asReader();

		if (delegateDecoder == null) return null;
		
		return delegateDecoder.decode(response, type);
	}

}
