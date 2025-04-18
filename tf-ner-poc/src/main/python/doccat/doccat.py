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

import re
import tensorflow as tf
import sys
from util import load_glove
from util import write_mapping
from math import floor
import random
import numpy as np

class Doccat:
    def __init__(self, vector_size=100):
        self.__vector_size = vector_size

    def load_data(self, file):
        with open(file, encoding="utf-8") as f:
            labels = []
            docs = []
            for line in f:
                parts = re.split(r'\t+', line)
                labels.append(parts[0].strip())
                docs.append(parts[1].strip())
        return labels, docs

    def create_placeholders(self):

        dropout_keep_prob = tf.placeholder(tf.float32, name="dropout_keep_prop")

        # shape is batch_size, and number of tokens
        token_ids_ph = tf.placeholder(tf.int32, shape=[None, None], name="token_ids")

        # shape is batch_size
        token_lengths_ph = tf.placeholder(tf.int32, shape=[None], name="token_lengths")

        # shape is batch_size
        y_ph = tf.placeholder(tf.int32, shape=[None], name="y")

        return dropout_keep_prob, token_ids_ph, token_lengths_ph, y_ph

    def create_graph(self, dropout_keep_prob, token_ids_ph, name_lengths_ph, y_ph, embedding_dict, nclasses):



        # This is a hack to make it load an embedding matrix larger than 2GB
        # Don't hardcode this 300
        embedding_placeholder = tf.placeholder(dtype=tf.float32, name="embedding_placeholder",
                                               shape=(len(embedding_dict), self.__vector_size))
        embedding_matrix = tf.Variable(embedding_placeholder, dtype=tf.float32, trainable=False, name="glove_embeddings")

        token_embeddings = tf.nn.embedding_lookup(embedding_matrix, token_ids_ph)


        char_hidden_size = 256
        cell_fw = tf.contrib.rnn.LSTMCell(char_hidden_size, state_is_tuple=True)
        cell_bw = tf.contrib.rnn.LSTMCell(char_hidden_size, state_is_tuple=True)

        _, ((_, output_fw), (_, output_bw)) = tf.nn.bidirectional_dynamic_rnn(cell_fw,
                                                                              cell_bw,
                                                                              token_embeddings,
                                                                              sequence_length=name_lengths_ph,
                                                                              dtype=tf.float32)

        output = tf.concat([output_fw, output_bw], axis=-1)

        output = tf.nn.dropout(output, dropout_keep_prob)

        W = tf.get_variable("W", shape=[2*char_hidden_size, nclasses])
        b = tf.get_variable("b", shape=[nclasses])
        logits = tf.nn.xw_plus_b(output, W, b, name="logits")

        # softmax ...
        probs = tf.exp(logits)
        norm_probs = tf.identity(probs / tf.reduce_sum(probs, 1, keepdims=True), name="norm_probs")

        loss = tf.nn.sparse_softmax_cross_entropy_with_logits(logits=logits, labels=y_ph)
        mean_loss = tf.reduce_mean(loss)

        train_op = tf.train.AdamOptimizer().minimize(loss)
        #train_op = tf.train.RMSPropOptimizer(learning_rate=0.001).minimize(loss)

        return embedding_placeholder, train_op, norm_probs


def encode_doc(word_dict, doc):
    encoded_doc = []
    for c in doc:
        if c in word_dict:
            encoded_doc.append(word_dict[c])
        else:
            encoded_doc.append(word_dict["__UNK__"])

    return encoded_doc


def mini_batch(label_dict, word_dict, labels, docs, batch_size, batch_index):
    begin = batch_size * batch_index
    end = min(batch_size * (batch_index + 1), len(labels))

    max_length = 0
    for i in range(begin, end):
        length = len(docs[i])
        if length > max_length:
            max_length = length

    doc_batch = []
    label_batch = []
    doc_length = []
    for i in range(begin, end):
        label_batch.append( label_dict[labels[i]])
        doc_batch.append(encode_doc(word_dict, docs[i]) + [0] * max(max_length - len(docs[i]), 0))
        doc_length.append(len(docs[i]))

    return label_batch, np.asarray(doc_batch), doc_length

def main():

    if len(sys.argv) != 5:
        print("Usage doccat.py embedding_file train_file dev_file test_file")
        return

    doccat = Doccat(100)

    labels_train, docs_train = doccat.load_data(sys.argv[2])
    labels_dev, docs_dev = doccat.load_data(sys.argv[3])
    labels_test, docs_test = doccat.load_data(sys.argv[4])


    word_dict, rev_word_dict, embeddings, vector_size = load_glove(sys.argv[1])

    # Encode labels into ids
    label_dict = {}
    for label in labels_train:
        if not label in label_dict:
            label_dict[label] = len(label_dict)


    dropout_keep_prob, token_ids_ph, token_lengths_ph, y_ph = doccat.create_placeholders()

    embedding_ph, train_op, probs_op = doccat.create_graph(dropout_keep_prob, token_ids_ph,
                                                           token_lengths_ph, y_ph,
                                                           embeddings, len(label_dict))

    sess = tf.Session(config=tf.ConfigProto(allow_soft_placement=True,
                                            log_device_placement=True))

    with sess.as_default():
        init=tf.global_variables_initializer()
        sess.run(init, feed_dict={embedding_ph: embeddings})
        batch_size = 20
        for epoch in range(50):
            print("Epoch " + str(epoch))
            acc_train = []

            batch_indexes = list(range(floor(len(docs_train) / batch_size)))
            random.Random(epoch).shuffle(batch_indexes)

            for batch_index in batch_indexes:
                label_train_batch, doc_train_batch, name_train_length = \
                    mini_batch(label_dict, word_dict, labels_train, docs_train, batch_size, batch_index)

                feed_dict = {dropout_keep_prob: 0.5, token_ids_ph: doc_train_batch, token_lengths_ph: name_train_length, y_ph: label_train_batch}
                _, probs = sess.run([train_op, probs_op], feed_dict)

                acc_train.append((batch_size - np.sum(np.minimum(np.abs(label_train_batch - np.argmax(probs, axis=1)),
                                                                 np.full((batch_size), 1)))) / batch_size)

            print("Train acc: " + str(np.mean(acc_train)))

            acc_dev = []
            for batch_index in range(floor(len(docs_dev) / batch_size)):
                label_dev_batch, doc_dev_batch, doc_dev_length = \
                    mini_batch(label_dict, word_dict, labels_dev, docs_dev, batch_size, batch_index)

                feed_dict = {dropout_keep_prob: 1, token_ids_ph: doc_dev_batch, token_lengths_ph: doc_dev_length, y_ph: label_dev_batch}
                probs = sess.run(probs_op, feed_dict)

                acc_dev.append((batch_size - np.sum(np.minimum(np.abs(label_dev_batch - np.argmax(probs, axis=1)),
                                                               np.full((batch_size), 1)))) / batch_size)

            print("Dev acc: " + str(np.mean(acc_dev)))

        with TemporaryDirectory() as temp_dir:
            temp_model_dir = temp_dir + "/model"

            builder = tf.saved_model.builder.SavedModelBuilder(temp_model_dir)
            builder.add_meta_graph_and_variables(sess, [tf.saved_model.tag_constants.SERVING])
            builder.save()

            write_mapping(label_dict, temp_model_dir + "/label_dict.txt")

            zipf = zipfile.ZipFile("doccat-" + str(epoch) +".zip", 'w', zipfile.ZIP_DEFLATED)

            for root, dirs, files in os.walk(temp_model_dir):
                for file in files:
                    modelFile = os.path.join(root, file)
                    zipf.write(modelFile, arcname=os.path.relpath(modelFile, temp_model_dir))

if __name__ == "__main__":
    main()
