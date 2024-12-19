# Licensed to the Apache Software Foundation (ASF) under one
# or more contributor license agreements.  See the NOTICE file
# distributed with this work for additional information
# regarding copyright ownership.  The ASF licenses this file
# to you under the Apache License, Version 2.0 (the
# "License"); you may not use this file except in compliance
# with the License.  You may obtain a copy of the License at
#
#   http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing,
# software distributed under the License is distributed on an
# "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
# KIND, either express or implied.  See the License for the
# specific language governing permissions and limitations
# under the License.

# Generated by the gRPC Python protocol compiler plugin. DO NOT EDIT!
"""Client and server classes corresponding to protobuf-defined services."""
import grpc
import warnings

import opennlp_pb2 as opennlp__pb2

GRPC_GENERATED_VERSION = '1.68.1'
GRPC_VERSION = grpc.__version__
_version_not_supported = False

try:
    from grpc._utilities import first_version_is_lower
    _version_not_supported = first_version_is_lower(GRPC_VERSION, GRPC_GENERATED_VERSION)
except ImportError:
    _version_not_supported = True

if _version_not_supported:
    raise RuntimeError(
        f'The grpc package installed is at version {GRPC_VERSION},'
        + f' but the generated code in opennlp_pb2_grpc.py depends on'
        + f' grpcio>={GRPC_GENERATED_VERSION}.'
        + f' Please upgrade your grpc module to grpcio>={GRPC_GENERATED_VERSION}'
        + f' or downgrade your generated code using grpcio-tools<={GRPC_VERSION}.'
    )


class PosTaggerServiceStub(object):
    """Missing associated documentation comment in .proto file."""

    def __init__(self, channel):
        """Constructor.

        Args:
            channel: A grpc.Channel.
        """
        self.Tag = channel.unary_unary(
                '/opennlp.PosTaggerService/Tag',
                request_serializer=opennlp__pb2.TagRequest.SerializeToString,
                response_deserializer=opennlp__pb2.StringList.FromString,
                _registered_method=True)
        self.TagWithContext = channel.unary_unary(
                '/opennlp.PosTaggerService/TagWithContext',
                request_serializer=opennlp__pb2.TagWithContextRequest.SerializeToString,
                response_deserializer=opennlp__pb2.StringList.FromString,
                _registered_method=True)
        self.GetAvailableModels = channel.unary_unary(
                '/opennlp.PosTaggerService/GetAvailableModels',
                request_serializer=opennlp__pb2.Empty.SerializeToString,
                response_deserializer=opennlp__pb2.AvailableModels.FromString,
                _registered_method=True)


class PosTaggerServiceServicer(object):
    """Missing associated documentation comment in .proto file."""

    def Tag(self, request, context):
        """Assigns the sentence of tokens POS tags.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def TagWithContext(self, request, context):
        """Assigns the sentence of tokens POS tags with additional (string-based) context.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def GetAvailableModels(self, request, context):
        """Returns the available models which can be used for POS tagging.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')


def add_PosTaggerServiceServicer_to_server(servicer, server):
    rpc_method_handlers = {
            'Tag': grpc.unary_unary_rpc_method_handler(
                    servicer.Tag,
                    request_deserializer=opennlp__pb2.TagRequest.FromString,
                    response_serializer=opennlp__pb2.StringList.SerializeToString,
            ),
            'TagWithContext': grpc.unary_unary_rpc_method_handler(
                    servicer.TagWithContext,
                    request_deserializer=opennlp__pb2.TagWithContextRequest.FromString,
                    response_serializer=opennlp__pb2.StringList.SerializeToString,
            ),
            'GetAvailableModels': grpc.unary_unary_rpc_method_handler(
                    servicer.GetAvailableModels,
                    request_deserializer=opennlp__pb2.Empty.FromString,
                    response_serializer=opennlp__pb2.AvailableModels.SerializeToString,
            ),
    }
    generic_handler = grpc.method_handlers_generic_handler(
            'opennlp.PosTaggerService', rpc_method_handlers)
    server.add_generic_rpc_handlers((generic_handler,))
    server.add_registered_method_handlers('opennlp.PosTaggerService', rpc_method_handlers)


 # This class is part of an EXPERIMENTAL API.
class PosTaggerService(object):
    """Missing associated documentation comment in .proto file."""

    @staticmethod
    def Tag(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.PosTaggerService/Tag',
            opennlp__pb2.TagRequest.SerializeToString,
            opennlp__pb2.StringList.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)

    @staticmethod
    def TagWithContext(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.PosTaggerService/TagWithContext',
            opennlp__pb2.TagWithContextRequest.SerializeToString,
            opennlp__pb2.StringList.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)

    @staticmethod
    def GetAvailableModels(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.PosTaggerService/GetAvailableModels',
            opennlp__pb2.Empty.SerializeToString,
            opennlp__pb2.AvailableModels.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)


class TokenizerTaggerServiceStub(object):
    """Missing associated documentation comment in .proto file."""

    def __init__(self, channel):
        """Constructor.

        Args:
            channel: A grpc.Channel.
        """
        self.Tokenize = channel.unary_unary(
                '/opennlp.TokenizerTaggerService/Tokenize',
                request_serializer=opennlp__pb2.TokenizeRequest.SerializeToString,
                response_deserializer=opennlp__pb2.StringList.FromString,
                _registered_method=True)
        self.TokenizePos = channel.unary_unary(
                '/opennlp.TokenizerTaggerService/TokenizePos',
                request_serializer=opennlp__pb2.TokenizePosRequest.SerializeToString,
                response_deserializer=opennlp__pb2.SpanList.FromString,
                _registered_method=True)
        self.GetAvailableModels = channel.unary_unary(
                '/opennlp.TokenizerTaggerService/GetAvailableModels',
                request_serializer=opennlp__pb2.Empty.SerializeToString,
                response_deserializer=opennlp__pb2.AvailableModels.FromString,
                _registered_method=True)


class TokenizerTaggerServiceServicer(object):
    """Missing associated documentation comment in .proto file."""

    def Tokenize(self, request, context):
        """Splits a sentence into its atomic parts.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def TokenizePos(self, request, context):
        """Finds the boundaries of atomic parts in a string.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def GetAvailableModels(self, request, context):
        """Returns the available models which can be used for tokenization tagging.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')


def add_TokenizerTaggerServiceServicer_to_server(servicer, server):
    rpc_method_handlers = {
            'Tokenize': grpc.unary_unary_rpc_method_handler(
                    servicer.Tokenize,
                    request_deserializer=opennlp__pb2.TokenizeRequest.FromString,
                    response_serializer=opennlp__pb2.StringList.SerializeToString,
            ),
            'TokenizePos': grpc.unary_unary_rpc_method_handler(
                    servicer.TokenizePos,
                    request_deserializer=opennlp__pb2.TokenizePosRequest.FromString,
                    response_serializer=opennlp__pb2.SpanList.SerializeToString,
            ),
            'GetAvailableModels': grpc.unary_unary_rpc_method_handler(
                    servicer.GetAvailableModels,
                    request_deserializer=opennlp__pb2.Empty.FromString,
                    response_serializer=opennlp__pb2.AvailableModels.SerializeToString,
            ),
    }
    generic_handler = grpc.method_handlers_generic_handler(
            'opennlp.TokenizerTaggerService', rpc_method_handlers)
    server.add_generic_rpc_handlers((generic_handler,))
    server.add_registered_method_handlers('opennlp.TokenizerTaggerService', rpc_method_handlers)


 # This class is part of an EXPERIMENTAL API.
class TokenizerTaggerService(object):
    """Missing associated documentation comment in .proto file."""

    @staticmethod
    def Tokenize(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.TokenizerTaggerService/Tokenize',
            opennlp__pb2.TokenizeRequest.SerializeToString,
            opennlp__pb2.StringList.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)

    @staticmethod
    def TokenizePos(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.TokenizerTaggerService/TokenizePos',
            opennlp__pb2.TokenizePosRequest.SerializeToString,
            opennlp__pb2.SpanList.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)

    @staticmethod
    def GetAvailableModels(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.TokenizerTaggerService/GetAvailableModels',
            opennlp__pb2.Empty.SerializeToString,
            opennlp__pb2.AvailableModels.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)


class SentenceDetectorServiceStub(object):
    """Missing associated documentation comment in .proto file."""

    def __init__(self, channel):
        """Constructor.

        Args:
            channel: A grpc.Channel.
        """
        self.sentDetect = channel.unary_unary(
                '/opennlp.SentenceDetectorService/sentDetect',
                request_serializer=opennlp__pb2.SentDetectRequest.SerializeToString,
                response_deserializer=opennlp__pb2.StringList.FromString,
                _registered_method=True)
        self.sentPosDetect = channel.unary_unary(
                '/opennlp.SentenceDetectorService/sentPosDetect',
                request_serializer=opennlp__pb2.SentDetectPosRequest.SerializeToString,
                response_deserializer=opennlp__pb2.SpanList.FromString,
                _registered_method=True)
        self.GetAvailableModels = channel.unary_unary(
                '/opennlp.SentenceDetectorService/GetAvailableModels',
                request_serializer=opennlp__pb2.Empty.SerializeToString,
                response_deserializer=opennlp__pb2.AvailableModels.FromString,
                _registered_method=True)


class SentenceDetectorServiceServicer(object):
    """Missing associated documentation comment in .proto file."""

    def sentDetect(self, request, context):
        """Detects sentences in a character sequence.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def sentPosDetect(self, request, context):
        """Detects sentences in a character sequence.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')

    def GetAvailableModels(self, request, context):
        """Returns the available models which can be used for sentence detection.
        """
        context.set_code(grpc.StatusCode.UNIMPLEMENTED)
        context.set_details('Method not implemented!')
        raise NotImplementedError('Method not implemented!')


def add_SentenceDetectorServiceServicer_to_server(servicer, server):
    rpc_method_handlers = {
            'sentDetect': grpc.unary_unary_rpc_method_handler(
                    servicer.sentDetect,
                    request_deserializer=opennlp__pb2.SentDetectRequest.FromString,
                    response_serializer=opennlp__pb2.StringList.SerializeToString,
            ),
            'sentPosDetect': grpc.unary_unary_rpc_method_handler(
                    servicer.sentPosDetect,
                    request_deserializer=opennlp__pb2.SentDetectPosRequest.FromString,
                    response_serializer=opennlp__pb2.SpanList.SerializeToString,
            ),
            'GetAvailableModels': grpc.unary_unary_rpc_method_handler(
                    servicer.GetAvailableModels,
                    request_deserializer=opennlp__pb2.Empty.FromString,
                    response_serializer=opennlp__pb2.AvailableModels.SerializeToString,
            ),
    }
    generic_handler = grpc.method_handlers_generic_handler(
            'opennlp.SentenceDetectorService', rpc_method_handlers)
    server.add_generic_rpc_handlers((generic_handler,))
    server.add_registered_method_handlers('opennlp.SentenceDetectorService', rpc_method_handlers)


 # This class is part of an EXPERIMENTAL API.
class SentenceDetectorService(object):
    """Missing associated documentation comment in .proto file."""

    @staticmethod
    def sentDetect(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.SentenceDetectorService/sentDetect',
            opennlp__pb2.SentDetectRequest.SerializeToString,
            opennlp__pb2.StringList.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)

    @staticmethod
    def sentPosDetect(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.SentenceDetectorService/sentPosDetect',
            opennlp__pb2.SentDetectPosRequest.SerializeToString,
            opennlp__pb2.SpanList.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)

    @staticmethod
    def GetAvailableModels(request,
            target,
            options=(),
            channel_credentials=None,
            call_credentials=None,
            insecure=False,
            compression=None,
            wait_for_ready=None,
            timeout=None,
            metadata=None):
        return grpc.experimental.unary_unary(
            request,
            target,
            '/opennlp.SentenceDetectorService/GetAvailableModels',
            opennlp__pb2.Empty.SerializeToString,
            opennlp__pb2.AvailableModels.FromString,
            options,
            channel_credentials,
            insecure,
            call_credentials,
            compression,
            wait_for_ready,
            timeout,
            metadata,
            _registered_method=True)
