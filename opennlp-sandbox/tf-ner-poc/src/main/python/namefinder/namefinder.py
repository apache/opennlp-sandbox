#
#  Licensed to the Apache Software Foundation (ASF) under one
#  or more contributor license agreements.  See the NOTICE file
#  distributed with this work for additional information
#  regarding copyright ownership.  The ASF licenses this file
#  to you under the Apache License, Version 2.0 (the
#  "License"); you may not use this file except in compliance
#  with the License.  You may obtain a copy of the License at
#
#    http://www.apache.org/licenses/LICENSE-2.0
#
#  Unless required by applicable law or agreed to in writing,
#  software distributed under the License is distributed on an
#  "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
#  KIND, either express or implied.  See the License for the
#  specific language governing permissions and limitations
#  under the License.
#

# This poc is based on source code taken from:
# https://github.com/guillaumegenthial/sequence_tagging

import sys
from math import floor
import tensorflow as tf
import re
import numpy as np
import zipfile
import os
from tempfile import TemporaryDirectory

# global variables for unknown word and numbers
__UNK__ = '__UNK__'
__NUM__ = '__NUM__'


# Parse the OpenNLP Name Finder format into begin, end, type triples
class NameSample:

    def __init__(self, line):
        self.tokens = []
        self.names = []
        start_regex = re.compile("<START(:([^:>\\s]*))?>")
        parts = line.split()
        start_index = -1
        word_index = 0
        for i in range(0, len(parts)):
            if start_regex.match(parts[i]):
                start_index = word_index
                name_type = start_regex.search(parts[i]).group(2)
                if name_type is None:
                    name_type = "default"
            elif parts[i] == "<END>":
                self.names.append((start_index, word_index, name_type))
            else:
                self.tokens.append(parts[i])
                word_index += 1


class VectorException(Exception):
    def __init__(self, value):
        self.value = value

    def __str__(self):
        return repr(self.value)


class NameFinder:
    label_dict = {}

    def __init__(self, use_lower_case_embeddings=False, vector_size=100):
        self.__vector_size = vector_size
        self.__use_lower_case_embeddings = use_lower_case_embeddings

    def load_data(self, word_dict, file):
        with open(file) as f:
            raw_data = f.readlines()

        sentences = []
        labels = []
        chars_set = set()

        for line in raw_data:
            name_sample = NameSample(line)
            sentence = []
            tokens = []

            if len(name_sample.tokens) == 0:
                continue

            for token in name_sample.tokens:

                chars_set.update(list(token))  # Add all chars to the set
                tokens.append(token)  # Add original token so chars can be encoded correctly later

                if self.__use_lower_case_embeddings:
                    token = token.lower()

                # TODO: implement NUM encoding

                if word_dict.get(token) is not None:
                    vector = word_dict[token]
                else:
                    vector = word_dict[__UNK__]

                sentence.append(vector)

            label = ["other"] * len(name_sample.tokens)
            for name in name_sample.names:
                label[name[0]] = name[2] + "-start"
                for i in range(name[0] + 1, name[1]):
                    label[i] = name[2] + "-cont"

            sentences.append((sentence, tokens))  # Add a tuple of list of word vectors and list of original words
            labels.append(label)

            for label_string in label:
                if label_string not in self.label_dict:
                    self.label_dict[label_string] = len(self.label_dict)

        return sentences, labels, chars_set

    def encode_labels(self, labels):
        return list(map(lambda l: self.label_dict[l], labels))

    def mini_batch(self, char_dict, sentences, labels, batch_size, batch_index):
        begin = batch_size * batch_index
        end = min(batch_size * (batch_index + 1), len(labels))

        # Determine the max sentence length in the batch
        max_length = 0
        for i in range(begin, end):
            length = len(sentences[i][0])
            if length > max_length:
                max_length = length

        sb = []
        lb = []
        seq_length = []
        for i in range(begin, end):
            sb.append(sentences[i][0] + [0] * max(max_length - len(sentences[i][0]), 0))
            lb.append(self.encode_labels(labels[i]) + [0] * max(max_length - len(labels[i]), 0))
            seq_length.append(len(sentences[i][0]))

        # Determine the max word length in the batch
        max_word_length = 0
        for i in range(begin, end):
            for word in sentences[i][1]:
                length = len(word)
                if length > max_word_length:
                    max_word_length = length

        cb = []
        wlb = []
        for i in range(begin, end):
            sentence_word_length = []
            sentence_word_chars = []

            for word in sentences[i][1]:
                word_chars = []
                for c in word:
                    word_chars.append(char_dict[c])

                sentence_word_length.append(len(word_chars))
                word_chars = word_chars + [0] * max(max_word_length - len(word_chars), 0)
                sentence_word_chars.append(word_chars)

            for i in range(max(max_length - len(sentence_word_chars), 0)):
                sentence_word_chars.append([0] * max_word_length)

            cb.append(sentence_word_chars)
            wlb.append(sentence_word_length + [0] * max(max_length - len(sentence_word_length), 0))

        return sb, cb, wlb, lb, seq_length

    # probably not necessary to pass in the embedding_dict, can be passed to init directly
    def create_graph(self, nchars, embedding_dict):

        dropout_keep_prob = tf.placeholder(tf.float32, name="dropout_keep_prop")

        with tf.variable_scope("chars"):
            # shape = (batch size, max length of sentence, max length of word)
            char_ids = tf.placeholder(tf.int32, shape=[None, None, None], name="char_ids")

            # shape = (batch_size, max_length of sentence)
            word_lengths_ph = tf.placeholder(tf.int32, shape=[None, None], name="word_lengths")

            dim_char = 100

            # 1. get character embeddings
            K = tf.get_variable(name="char_embeddings", dtype=tf.float32,
                                shape=[nchars, dim_char])

            # shape = (batch, sentence, word, dim of char embeddings)
            char_embeddings = tf.nn.embedding_lookup(K, char_ids)

            # 2. put the time dimension on axis=1 for dynamic_rnn
            s = tf.shape(char_embeddings)  # store old shape
            # shape = (batch x sentence, word, dim of char embeddings)
            char_embeddings = tf.reshape(char_embeddings, shape=[s[0] * s[1], s[-2], dim_char])
            word_lengths = tf.reshape(word_lengths_ph, shape=[s[0] * s[1]])

            # 3. bi lstm on chars
            char_hidden_size = 100
            cell_fw = tf.contrib.rnn.LSTMCell(char_hidden_size, state_is_tuple=True)
            cell_bw = tf.contrib.rnn.LSTMCell(char_hidden_size, state_is_tuple=True)

            _, ((_, output_fw), (_, output_bw)) = tf.nn.bidirectional_dynamic_rnn(cell_fw,
                                                                                  cell_bw,
                                                                                  char_embeddings,
                                                                                  sequence_length=word_lengths,
                                                                                  dtype=tf.float32)
            # shape = (batch x sentence, 2 x char_hidden_size)
            output = tf.concat([output_fw, output_bw], axis=-1)

            # shape = (batch, sentence, 2 x char_hidden_size)
            char_rep = tf.reshape(output, shape=[-1, s[1], 2 * char_hidden_size])

        with tf.variable_scope("words"):
            token_ids = tf.placeholder(tf.int32, shape=[None, None], name="word_ids")
            sequence_lengths = tf.placeholder(tf.int32, shape=[None], name="sequence_lengths")

            # This is a hack to make it load an embedding matrix larger than 2GB
            # Don't hardcode this 300
            embedding_placeholder = tf.placeholder(dtype=tf.float32, name="embedding_placeholder",
                                                   shape=(len(embedding_dict), self.__vector_size))
            embedding_matrix = tf.Variable(embedding_placeholder, dtype=tf.float32, trainable=False,
                                           name="glove_embeddings")

            token_embeddings = tf.nn.embedding_lookup(embedding_matrix, token_ids)

            # shape = (batch, sentence, 2 x char_hidden_size + word_vector_size)
            word_embeddings = tf.concat([token_embeddings, char_rep], axis=-1)

            word_embeddings = tf.nn.dropout(word_embeddings, dropout_keep_prob)

        hidden_size = 300

        # Lets add a char lstm layer to reproduce the state of the art results ...

        with tf.variable_scope("bi-lstm"):
            # Add LSTM layer
            cell_fw = tf.contrib.rnn.LSTMCell(hidden_size)
            cell_bw = tf.contrib.rnn.LSTMCell(hidden_size)

            (output_fw, output_bw), _ = tf.nn.bidirectional_dynamic_rnn(cell_fw, cell_bw, word_embeddings,
                                                                        sequence_length=sequence_lengths,
                                                                        dtype=tf.float32)

            context_rep = tf.concat([output_fw, output_bw], axis=-1)

            context_rep = tf.nn.dropout(context_rep, dropout_keep_prob)

            labels = tf.placeholder(tf.int32, shape=[None, None], name="labels")

        ntags = len(self.label_dict)

        W = tf.get_variable("W", shape=[2 * hidden_size, ntags], dtype=tf.float32)
        b = tf.get_variable("b", shape=[ntags], dtype=tf.float32, initializer=tf.zeros_initializer())
        ntime_steps = tf.shape(context_rep)[1]
        context_rep_flat = tf.reshape(context_rep, [-1, 2 * hidden_size])
        pred = tf.matmul(context_rep_flat, W) + b
        self.logits = tf.reshape(pred, [-1, ntime_steps, ntags], name="logits")

        log_likelihood, transition_params = tf.contrib.crf.crf_log_likelihood(
            self.logits, labels, sequence_lengths)

        self.transition_params = tf.identity(transition_params, name="trans_params")

        loss = tf.reduce_mean(-log_likelihood)

        train_op = tf.train.AdamOptimizer().minimize(loss)

        return embedding_placeholder, token_ids, char_ids, word_lengths_ph, \
               sequence_lengths, labels, dropout_keep_prob, train_op

    def predict_batch(self, sess, token_ids_ph, char_ids_ph, word_lengths_ph,
                      sequence_lengths_ph, sentences, char_ids, word_length, lengths, dropout_keep_prob):

        feed_dict = {token_ids_ph: sentences, char_ids_ph: char_ids, word_lengths_ph: word_length,
                     sequence_lengths_ph: lengths, dropout_keep_prob: 1}

        viterbi_sequences = []
        logits, trans_params = sess.run([self.logits, self.transition_params], feed_dict=feed_dict)

        for logit, sequence_length in zip(logits, lengths):
            if sequence_length != 0:
                logit = logit[:sequence_length]  # keep only the valid steps
                viterbi_seq, viterbi_score = tf.contrib.crf.viterbi_decode(logit, trans_params)
                viterbi_sequences += [viterbi_seq]
            else:
                viterbi_sequences += []

        return viterbi_sequences, lengths


def get_chunk_type(tok, idx_to_tag):
    tag_name = idx_to_tag[tok]
    tag_class = tag_name.split('-')[0]
    tag_type = tag_name.split('-')[-1]
    return tag_class, tag_type


def get_chunks(seq, tags):
    default = tags["other"]
    idx_to_tag = {idx: tag for tag, idx in tags.items()}
    chunks = []
    chunk_type, chunk_start = None, None
    for i, tok in enumerate(seq):
        # End of a chunk 1
        if tok == default and chunk_type is not None:
            # Add a chunk.
            chunk = (chunk_type, chunk_start, i)
            chunks.append(chunk)
            chunk_type, chunk_start = None, None

        # End of a chunk + start of a chunk!
        elif tok != default:
            tok_chunk_class, tok_chunk_type = get_chunk_type(tok, idx_to_tag)
            if chunk_type is None:
                chunk_type, chunk_start = tok_chunk_type, i
            elif tok_chunk_type != chunk_type or tok_chunk_class == "B":
                chunk = (chunk_type, chunk_start, i)
                chunks.append(chunk)
                chunk_type, chunk_start = tok_chunk_type, i
        else:
            pass

    # end condition
    if chunk_type is not None:
        chunk = (chunk_type, chunk_start, len(seq))
        chunks.append(chunk)

    return chunks


def write_mapping(tags, output_filename):
    with open(output_filename, 'w', encoding='utf-8') as f:
        for (tag, i) in sorted(tags.items(), key=lambda x: x[1]):
            f.write('{}\n'.format(tag))


def load_glove(glove_file):
    with open(glove_file) as f:

        word_dict = {}
        embeddings = []

        vector_size = -1

        for line in f:
            parts = line.strip().split(" ")

            if vector_size == -1:
                if len(parts) == 2:
                    vector_size = int(parts[1])
                    continue
                vector_size = len(parts) - 1

            if len(parts) != vector_size + 1:
                raise VectorException("Bad Vector in line: {}, size: {} vector: {}".format(len(line), len(parts), line))
                continue
            word_dict[parts[0]] = len(word_dict)
            embeddings.append(np.array(parts[1:], dtype=np.float32))

    # add unknown word symbol and number symbol
    if __UNK__ not in word_dict:
        word_dict[__UNK__] = len(word_dict)
        unk_random = 0.08 * np.random.random_sample(vector_size) - 0.04
        embeddings.append(unk_random.astype(np.float32))
    if __NUM__ not in word_dict:
        word_dict[__NUM__] = len(word_dict)
        embeddings.append(np.zeros(vector_size, dtype=np.float32))

    # Create a reverse word dict
    rev_word_dict = {}
    for word, id in word_dict.items():
        rev_word_dict[id] = word

    return word_dict, rev_word_dict, np.asarray(embeddings), vector_size


def main():
    if len(sys.argv) != 5:
        print("Usage namefinder.py embedding_file train_file dev_file test_file")
        return

    word_dict, rev_word_dict, embeddings, vector_size = load_glove(sys.argv[1])

    name_finder = NameFinder(vector_size)

    sentences, labels, char_set = name_finder.load_data(word_dict, sys.argv[2])
    sentences_dev, labels_dev, char_set_dev = name_finder.load_data(word_dict, sys.argv[3])

    char_dict = {k: v for v, k in enumerate(char_set | char_set_dev)}

    embedding_ph, token_ids_ph, char_ids_ph, word_lengths_ph, sequence_lengths_ph, labels_ph, dropout_keep_prob, train_op \
        = name_finder.create_graph(len(char_set | char_set_dev), embeddings)

    sess = tf.Session(config=tf.ConfigProto(allow_soft_placement=True,
                                            log_device_placement=True))

    best_f1 = 0.0
    no_improvement = 0
    with sess.as_default():
        init = tf.global_variables_initializer()
        sess.run(init, feed_dict={embedding_ph: embeddings})

        batch_size = 20
        for epoch in range(100):
            print("Epoch " + str(epoch))

            for batch_index in range(floor(len(sentences) / batch_size)):
                if batch_index % 200 == 0:
                    print("batch_index " + str(batch_index))

                # mini_batch should also return char_ids and word length ...
                sentences_batch, chars_batch, word_length_batch, labels_batch, lengths = \
                    name_finder.mini_batch(char_dict, sentences, labels, batch_size, batch_index)

                feed_dict = {token_ids_ph: sentences_batch, char_ids_ph: chars_batch,
                             word_lengths_ph: word_length_batch, sequence_lengths_ph: lengths,
                             labels_ph: labels_batch, dropout_keep_prob: 0.5}

                train_op.run(feed_dict, sess)

            accs = []
            correct_preds, total_correct, total_preds = 0., 0., 0.
            for batch_index in range(floor(len(sentences_dev) / batch_size)):
                sentences_test_batch, chars_batch_test, word_length_batch_test, \
                labels_test_batch, length_test = name_finder.mini_batch(char_dict,
                                                                        sentences_dev,
                                                                        labels_dev,
                                                                        batch_size,
                                                                        batch_index)

                labels_pred, sequence_lengths = name_finder.predict_batch(
                    sess, token_ids_ph, char_ids_ph, word_lengths_ph, sequence_lengths_ph,
                    sentences_test_batch, chars_batch_test, word_length_batch_test, length_test, dropout_keep_prob)

                for lab, lab_pred, length in zip(labels_test_batch, labels_pred,
                                                 sequence_lengths):
                    lab = lab[:length]
                    lab_pred = lab_pred[:length]
                    accs += [a == b for (a, b) in zip(lab, lab_pred)]

                    lab_chunks = set(get_chunks(lab, name_finder.label_dict))
                    lab_pred_chunks = set(get_chunks(lab_pred, name_finder.label_dict))

                    correct_preds += len(lab_chunks & lab_pred_chunks)
                    total_preds += len(lab_pred_chunks)
                    total_correct += len(lab_chunks)

            p = correct_preds / total_preds if correct_preds > 0 else 0
            r = correct_preds / total_correct if correct_preds > 0 else 0
            f1 = 2 * p * r / (p + r) if correct_preds > 0 else 0
            acc = np.mean(accs)

            if f1 > best_f1:

                best_f1 = f1
                no_improvement = 0

                with TemporaryDirectory() as temp_dir:
                    temp_model_dir = temp_dir + "/model"

                    builder = tf.saved_model.builder.SavedModelBuilder(temp_model_dir)
                    builder.add_meta_graph_and_variables(sess, [tf.saved_model.tag_constants.SERVING])
                    builder.save()

                    write_mapping(word_dict, temp_model_dir + '/word_dict.txt')
                    write_mapping(name_finder.label_dict, temp_model_dir + "/label_dict.txt")
                    write_mapping(char_dict, temp_model_dir + "/char_dict.txt")

                    zipf = zipfile.ZipFile("namefinder-" + str(epoch) + ".zip", 'w', zipfile.ZIP_DEFLATED)

                    for root, dirs, files in os.walk(temp_model_dir):
                        for file in files:
                            modelFile = os.path.join(root, file)
                            zipf.write(modelFile, arcname=os.path.relpath(modelFile, temp_model_dir))
            else:
                no_improvement += 1

            print("ACC " + str(acc))
            print("F1  " + str(f1) + "  P " + str(p) + "  R " + str(r))

            if no_improvement > 5:
                print("No further improvement. Stopping.")
                break


if __name__ == "__main__":
    main()
