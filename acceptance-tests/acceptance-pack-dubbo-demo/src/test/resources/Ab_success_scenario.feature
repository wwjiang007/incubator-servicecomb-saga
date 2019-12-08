# Licensed to the Apache Software Foundation (ASF) under one or more
# contributor license agreements.  See the NOTICE file distributed with
# this work for additional information regarding copyright ownership.
# The ASF licenses this file to You under the Apache License, Version 2.0
# (the "License"); you may not use this file except in compliance with
# the License.  You may obtain a copy of the License at
#
#      http://www.apache.org/licenses/LICENSE-2.0
#
# Unless required by applicable law or agreed to in writing, software
# distributed under the License is distributed on an "AS IS" BASIS,
# WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
# See the License for the specific language governing permissions and
# limitations under the License.

Feature: Alpha records transaction events

  Scenario: A->B, success
    Given ServiceA is up and running
    And ServiceB is up and running
    And Alpha is up and running

    When Ab :: A->B, success

    Then Alpha records the following events
      | serviceName  | type             |
      | servicea | SagaStartedEvent |
      | servicea     | TxStartedEvent   |
      | serviceb    | TxStartedEvent     |
      | serviceb   | TxEndedEvent   |
      | servicea   | TxEndedEvent     |
      | * | SagaEndedEvent   |

    And servicea success update status
      | service | vstatus |
      | servicea | run |

    And serviceb success update status
      | service | vstatus |
      | serviceb | run |