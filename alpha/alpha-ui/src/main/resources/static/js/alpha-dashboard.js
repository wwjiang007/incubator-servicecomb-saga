/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

$(document).ready(function () {
  $.ajax('/ui/transaction/statistics', {
    success: function (data) {
      $('#statistics-total').text(digitUnit(data.total,2));
      $('#statistics-successful').text(digitUnit(data.successful,2));
      $('#statistics-compensated').text(digitUnit(data.compensated,2));
      $('#statistics-failed').text(digitUnit(data.failed,2));
      $('#statistics-total-tip').text(parseInt(data.total).toLocaleString());
      $('#statistics-successful-tip').text(parseInt(data.successful).toLocaleString());
      $('#statistics-compensated-tip').text(parseInt(data.compensated).toLocaleString());
      $('#statistics-failed-tip').text(parseInt(data.failed).toLocaleString());
    },
    error: function (state) {
      // TODO show message
    }
  });

  $.ajax('/ui/transaction/slow', {
    success: function (data) {
      for (i = 0; i < data.length; i++) {
        $('.slow-topn').append(
            '<a href="/ui/transaction/' + data[i].globalTxId
            + '"><div class="progress mb-3" id="slow-top-"' + i + '>\n'
            + '<div class="progress-bar" role="progressbar" style="cursor:pointer; width: '
            + (data[i].durationTime / data[0].durationTime) * 100
            + '%" aria-valuenow="75" aria-valuemin="0" aria-valuemax="100">'
            + parseInt(data[i].durationTime).toLocaleString() + ' ms</div>\n'
            + '</div></a>')
      }
    },
    error: function (state) {
      // TODO show message
    }
  });

  $.ajax('/ui/transaction/metrics', {
    success: function (metrics) {
      refreshActiveTransactionCard(metrics);
    },
    error: function (state) {
      // TODO show message
    }
  });

  var socket = new SockJS('/websocket-config');
  stompClient = Stomp.over(socket);
  stompClient.connect({}, function (frame) {
    console.log('Connected: ' + frame);
    stompClient.subscribe('/topic/metrics', function (metrics) {
      //console.log(JSON.parse(metrics.body).content)
      refreshActiveTransactionCard(JSON.parse(metrics.body))
    });
  });

  function refreshActiveTransactionCard(data){
    //events
    $('#metrics-events-received').text(parseInt(data.metrics.eventReceived).toLocaleString());
    $('#metrics-events-accepted').text(parseInt(data.metrics.eventAccepted).toLocaleString());
    $('#metrics-events-rejected').text(parseInt(data.metrics.eventRejected).toLocaleString());
    $('#metrics-events-average-time').text(data.metrics.eventAvgTime+' ms / event');
    $('#metrics-events-received-progress').css('width',data.metrics.eventReceived==0?'0%':'100%');
    $('#metrics-events-accepted-progress').css('width',(data.metrics.eventAccepted/data.metrics.eventReceived)*100+'%');
    $('#metrics-events-rejected-progress').css('width',(data.metrics.eventRejected/data.metrics.eventReceived)*100+'%');
    //actors
    $('#metrics-actors-received').text(parseInt(data.metrics.actorReceived).toLocaleString());
    $('#metrics-actors-accepted').text(parseInt(data.metrics.actorAccepted).toLocaleString());
    $('#metrics-actors-rejected').text(parseInt(data.metrics.actorRejected).toLocaleString());
    $('#metrics-actors-average-time').text(data.metrics.actorAvgTime+' ms / event');
    $('#metrics-actors-received-progress').css('width',data.metrics.actorReceived==0?'0%':'100%');
    $('#metrics-actors-accepted-progress').css('width',(data.metrics.actorAccepted/data.metrics.actorReceived)*100+'%');
    $('#metrics-actors-rejected-progress').css('width',(data.metrics.actorRejected/data.metrics.actorReceived)*100+'%');
    //persistence
    $('#metrics-persistence-received').text(parseInt(data.metrics.repositoryReceived).toLocaleString());
    $('#metrics-persistence-accepted').text(parseInt(data.metrics.repositoryAccepted).toLocaleString());
    $('#metrics-persistence-rejected').text(parseInt(data.metrics.repositoryRejected).toLocaleString());
    $('#metrics-persistence-average-time').text(data.metrics.repositoryAvgTime+' ms / transaction');
    $('#metrics-persistence-received-progress').css('width',data.metrics.repositoryReceived==0?'0%':'100%');
    $('#metrics-persistence-accepted-progress').css('width',(data.metrics.repositoryAccepted/data.metrics.repositoryReceived)*100+'%');
    $('#metrics-persistence-rejected-progress').css('width',(data.metrics.repositoryRejected/data.metrics.repositoryReceived)*100+'%');
    //saga
    $('#metrics-saga-begin').text(parseInt(data.metrics.sagaBeginCounter).toLocaleString());
    $('#metrics-saga-end').text(parseInt(data.metrics.sagaEndCounter).toLocaleString());
    $('#metrics-saga-average-time').text(data.metrics.sagaAvgTime+' ms / transaction');
    $('#metrics-saga-begin-progress').css('width',data.metrics.sagaBeginCounter==0?'0%':'100%');
    $('#metrics-saga-end-progress').css('width',(data.metrics.sagaEndCounter/data.metrics.sagaBeginCounter)*100+'%');
    //counter
    $('#metrics-committed').text(digitUnit(data.metrics.committed,2));
    $('#metrics-compensated').text(digitUnit(data.metrics.compensated,2));
    $('#metrics-suspended').text(digitUnit(data.metrics.suspended,2));
    $('#metrics-committed-tip').text(parseInt(data.metrics.committed).toLocaleString());
    $('#metrics-compensated-tip').text(parseInt(data.metrics.compensated).toLocaleString());
    $('#metrics-suspended-tip').text(parseInt(data.metrics.suspended).toLocaleString());
  }

  function digitUnit(n, d) {
    x = ('' + n).length, p = Math.pow, d = p(10, d);
    x -= x % 3;
    more = Math.round(n * d / p(10, x)) % d;
    y = Math.round(n * d / p(10, x)) / d + " kMGTPE"[x / 3];
    return more == 0 ? y : y + '+';
  }

  function millisToMinutesAndSeconds(millis) {
    var minutes = Math.floor(millis / 60000);
    var seconds = ((millis % 60000) / 1000).toFixed(0);
    return minutes + ":" + (seconds < 10 ? '0' : '') + seconds;
  }
});