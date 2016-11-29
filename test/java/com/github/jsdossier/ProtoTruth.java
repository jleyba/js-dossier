/*
 Copyright 2013-2016 Jason Leyba

 Licensed under the Apache License, Version 2.0 (the "License");
 you may not use this file except in compliance with the License.
 You may obtain a copy of the License at

   http://www.apache.org/licenses/LICENSE-2.0

 Unless required by applicable law or agreed to in writing, software
 distributed under the License is distributed on an "AS IS" BASIS,
 WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 See the License for the specific language governing permissions and
 limitations under the License.
 */

package com.github.jsdossier;

import static com.google.common.collect.Iterables.transform;
import static com.google.common.truth.Truth.assertAbout;
import static com.google.common.truth.Truth.assertThat;
import static com.google.protobuf.TextFormat.printToString;

import com.google.common.base.Function;
import com.google.common.truth.FailureStrategy;
import com.google.common.truth.Subject;
import com.google.common.truth.SubjectFactory;
import com.google.protobuf.MessageOrBuilder;

import java.util.Arrays;

import javax.annotation.Nullable;

final class ProtoTruth {
  private ProtoTruth() {}

  public static MessageSubject assertMessage(MessageOrBuilder message) {
    return assertAbout(new SubjectFactory<MessageSubject, MessageOrBuilder>() {
      @Override
      public MessageSubject getSubject(FailureStrategy failureStrategy, MessageOrBuilder message) {
        return new MessageSubject(failureStrategy, message);
      }
    }).that(message);
  }

  public static MessageListSubject assertMessages(
      Iterable<? extends MessageOrBuilder> messages) {
    return assertAbout(
        new SubjectFactory<MessageListSubject, Iterable<? extends MessageOrBuilder>>() {
          @Override
          public MessageListSubject getSubject(
              FailureStrategy failureStrategy, Iterable<? extends MessageOrBuilder> subject) {
            return new MessageListSubject(failureStrategy, subject);
          }
        }).that(messages);
  }

  static final class MessageListSubject
      extends Subject<MessageListSubject, Iterable<? extends MessageOrBuilder>> {

    private MessageListSubject(
        FailureStrategy failureStrategy, @Nullable Iterable<? extends MessageOrBuilder> subject) {
      super(failureStrategy, subject);
    }

    public void isEmpty() {
      assertThat(actual()).isEmpty();
    }

    public void containsExactly(MessageOrBuilder... messages) {
      Iterable<String> actual = transform(actual(), new Function<MessageOrBuilder, String>() {
        @Nullable
        @Override
        public String apply(MessageOrBuilder input) {
          return printToString(input);
        }
      });


      Iterable<MessageOrBuilder> msgs = Arrays.asList(messages);
      Iterable<String> expected = transform(msgs, new Function<MessageOrBuilder, String>() {
        @Nullable
        @Override
        public String apply(MessageOrBuilder input) {
          return printToString(input);
        }
      });

      assertThat(String.valueOf(actual)).isEqualTo(String.valueOf(expected));
    }
  }

  static final class MessageSubject
      extends Subject<MessageSubject, MessageOrBuilder> {

    private MessageSubject(FailureStrategy failureStrategy, @Nullable MessageOrBuilder subject) {
      super(failureStrategy, subject);
    }

    @Override
    public void isEqualTo(@Nullable Object other) {
      if (other instanceof MessageOrBuilder) {
        this.isNotNull();
        assertThat(printToString(actual()))
            .isEqualTo(printToString((MessageOrBuilder) other));
      } else {
        super.isEqualTo(other);
      }
    }
  }
}
