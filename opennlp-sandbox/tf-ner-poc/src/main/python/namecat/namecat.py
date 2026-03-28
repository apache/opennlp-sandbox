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
from math import floor
import numpy as np
import random
import zipfile
import os
from tempfile import TemporaryDirectory

def load_data(file):
    with open(file, encoding="utf-8") as f:
        labels = []
        names = []
        for line in f:
            parts = re.split(r'\t+', line)
            labels.append(parts[0].strip())
            names.append(parts[1].strip())
    return labels, names

# create placeholders
def create_placeholders():

    dropout_keep_prob = tf.placeholder(tf.float32, name="dropout_keep_prop")

    # shape is batch_size, and length of name
    char_ids_ph = tf.placeholder(tf.int32, shape=[None, None], name="char_ids")

    # shape is batch_size
    name_lengths_ph = tf.placeholder(tf.int32, shape=[None], name="name_lengths")

    # shape is batch_size
    y_ph = tf.placeholder(tf.int32, shape=[None], name="y")
    return dropout_keep_prob, char_ids_ph, name_lengths_ph, y_ph

def create_graph(dropout_keep_prob, char_ids_ph, name_lengths_ph, y_ph, nchars, nclasses):


    dim_char = 100

    K = tf.get_variable(name="char_embeddings", dtype=tf.float32,
                        shape=[nchars, dim_char])

    char_embeddings = tf.nn.embedding_lookup(K, char_ids_ph)

    char_embeddings = tf.nn.dropout(char_embeddings, dropout_keep_prob)

    char_hidden_size = 256
    cell_fw = tf.contrib.rnn.LSTMCell(char_hidden_size, state_is_tuple=True)
    cell_bw = tf.contrib.rnn.LSTMCell(char_hidden_size, state_is_tuple=True)

    _, ((_, output_fw), (_, output_bw)) = tf.nn.bidirectional_dynamic_rnn(cell_fw,
                                                                      cell_bw,
                                                                      char_embeddings,
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

    return train_op, norm_probs


def encode_name(char_dict, name):
    encoded_name = []
    for c in name:
        encoded_name.append(char_dict[c])
    return encoded_name

def mini_batch(label_dict, char_dict, labels, names, batch_size, batch_index):
    begin = batch_size * batch_index
    end = min(batch_size * (batch_index + 1), len(labels))

    max_length = 0
    for i in range(begin, end):
        length = len(names[i])
        if length > max_length:
            max_length = length

    name_batch = []
    label_batch = []
    name_length = []
    for i in range(begin, end):
        label_batch.append( label_dict[labels[i]])
        name_batch.append(encode_name(char_dict, names[i]) + [0] * max(max_length - len(names[i]), 0))
        name_length.append(len(names[i]))

    return label_batch, np.asarray(name_batch), name_length

def write_mapping(tags, output_filename):
    with open(output_filename, 'w', encoding='utf-8') as f:
        for i, tag in enumerate(tags):
            f.write(tag)
            f.write("\n")

def main():

    if len(sys.argv) != 4:
        print("Usage namecat.py train_file dev_file test_file")
        return

    labels_train, names_train = load_data(sys.argv[1])
    labels_dev, names_dev = load_data(sys.argv[2])
    labels_test, names_test = load_data(sys.argv[3])

    # Encode labels into ids
    label_dict = {}
    for label in labels_train:
        if not label in label_dict:
            label_dict[label] = len(label_dict)

    # Create char dict from names ...

    char_set = set()
    for name in names_train + names_dev + names_train:
        char_set = char_set.union(name)

    char_dict = {k: v for v, k in enumerate(char_set)}
    char_dict[chr(0)] = 0

    dropout_keep_prob, char_ids_ph, name_lengths_ph, y_ph = create_placeholders()

    train_op, probs_op = create_graph(dropout_keep_prob, char_ids_ph, name_lengths_ph, y_ph, len(char_set), len(label_dict))

    sess = tf.Session(config=tf.ConfigProto(allow_soft_placement=True,
                                            log_device_placement=True))

    with sess.as_default():
        init=tf.global_variables_initializer()
        sess.run(init)

        batch_size = 20
        for epoch in range(20):
            print("Epoch " + str(epoch))
            acc_train = []

            batch_indexes = list(range(floor(len(names_train) / batch_size)))

            # Shuffle the data
            combined = list(zip(names_train, labels_train))
            random.shuffle(combined)
            names_train[:], labels_train[:] = zip(*combined)

            for batch_index in batch_indexes:
                label_train_batch, name_train_batch, name_train_length = \
                    mini_batch(label_dict, char_dict, labels_train, names_train, batch_size, batch_index)

                # Add char dropout here ...
                for i, j in np.ndindex(name_train_batch.shape):
                    if random.uniform(0, 1) <= 0.0005:
                        name_train_batch[i][j] = 0

                feed_dict = {dropout_keep_prob: 0.5, char_ids_ph: name_train_batch, name_lengths_ph: name_train_length, y_ph: label_train_batch}
                _, probs = sess.run([train_op, probs_op], feed_dict)

                acc_train.append((batch_size - np.sum(np.minimum(np.abs(label_train_batch - np.argmax(probs, axis=1)),
                                                                        np.full((batch_size), 1)))) / batch_size)

            print("Train acc: " + str(np.mean(acc_train)))

            acc_dev = []
            for batch_index in range(floor(len(names_dev) / batch_size)):
                label_dev_batch, name_dev_batch, name_dev_length = \
                    mini_batch(label_dict, char_dict, labels_dev, names_dev, batch_size, batch_index)

                feed_dict = {dropout_keep_prob: 1, char_ids_ph: name_dev_batch, name_lengths_ph: name_dev_length, y_ph: label_dev_batch}
                probs = sess.run(probs_op, feed_dict)

                acc_dev.append((batch_size - np.sum(np.minimum(np.abs(label_dev_batch - np.argmax(probs, axis=1)),
                                                               np.full((batch_size), 1)))) / batch_size)

            print("Dev acc: " + str(np.mean(acc_dev)))

        #acc_test = []
        #for batch_index in range(floor(len(names_test) / batch_size)):
        #    label_test_batch, name_test_batch, name_test_length = \
        #        mini_batch(label_dict, char_dict, labels_test, names_test, batch_size, batch_index)

        #    feed_dict = {char_ids_ph: name_test_batch, name_lengths_ph: name_test_length, y_ph: label_test_batch}
        #    probs = sess.run(probs_op, feed_dict)

        #    acc_test.append((batch_size  - np.sum(np.abs(label_test_batch - np.argmax(probs, axis=1)))) / batch_size)

        #print("Test acc: " + str(np.mean(acc_test)))

        with TemporaryDirectory() as temp_dir:
            temp_model_dir = temp_dir + "/model"

            builder = tf.saved_model.builder.SavedModelBuilder(temp_model_dir)
            builder.add_meta_graph_and_variables(sess, [tf.saved_model.tag_constants.SERVING])
            builder.save()

            write_mapping(label_dict, temp_model_dir + "/label_dict.txt")
            write_mapping(char_dict, temp_model_dir + "/char_dict.txt")

            zipf = zipfile.ZipFile("namecat-" + str(epoch) +".zip", 'w', zipfile.ZIP_DEFLATED)

            for root, dirs, files in os.walk(temp_model_dir):
                for file in files:
                    modelFile = os.path.join(root, file)
                    zipf.write(modelFile, arcname=os.path.relpath(modelFile, temp_model_dir))

if __name__ == "__main__":
    main()
