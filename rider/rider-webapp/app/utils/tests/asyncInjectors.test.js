/*-
 * <<
 * wormhole
 * ==
 * Copyright (C) 2016 - 2017 EDP
 * ==
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 * 
 *      http://www.apache.org/licenses/LICENSE-2.0
 * 
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 * >>
 */

/**
 * Test async injectors
 */

import expect from 'expect'
import configureStore from '../../store'
import { memoryHistory } from 'react-router'
import { put } from 'redux-saga/effects'
import { fromJS } from 'immutable'

import {
  injectAsyncReducer,
  injectAsyncSagas,
  getAsyncInjectors
} from '../asyncInjectors'

// Fixtures

const initialState = fromJS({ reduced: 'soon' })

const reducer = (state = initialState, action) => {
  switch (action.type) {
    case 'TEST':
      return state.set('reduced', action.payload)
    default:
      return state
  }
}

function* testSaga () {
  yield put({ type: 'TEST', payload: 'yup' })
}

const sagas = [
  testSaga
]

describe('asyncInjectors', () => {
  let store

  describe('getAsyncInjectors', () => {
    before(() => {
      store = configureStore({}, memoryHistory)
    })

    it('given a store, should return all async injectors', () => {
      const { injectReducer, injectSagas } = getAsyncInjectors(store)

      injectReducer('test', reducer)
      injectSagas(sagas)

      const actual = store.getState().get('test')
      const expected = initialState.merge({ reduced: 'yup' })

      expect(actual.toJS()).toEqual(expected.toJS())
    })

    it('should throw if passed invalid store shape', () => {
      let result = false

      Reflect.deleteProperty(store, 'dispatch')

      try {
        getAsyncInjectors(store)
      } catch (err) {
        result = err.name === 'Invariant Violation'
      }

      expect(result).toEqual(true)
    })
  })

  describe('helpers', () => {
    before(() => {
      store = configureStore({}, memoryHistory)
    })

    describe('injectAsyncReducer', () => {
      it('given a store, it should provide a function to inject a reducer', () => {
        const injectReducer = injectAsyncReducer(store)

        injectReducer('test', reducer)

        const actual = store.getState().get('test')
        const expected = initialState

        expect(actual.toJS()).toEqual(expected.toJS())
      })

      it('should throw if passed invalid name', () => {
        let result = false

        const injectReducer = injectAsyncReducer(store)

        try {
          injectReducer('', reducer)
        } catch (err) {
          result = err.name === 'Invariant Violation'
        }

        try {
          injectReducer(999, reducer)
        } catch (err) {
          result = err.name === 'Invariant Violation'
        }

        expect(result).toEqual(true)
      })

      it('should throw if passed invalid reducer', () => {
        let result = false

        const injectReducer = injectAsyncReducer(store)

        try {
          injectReducer('bad', 'nope')
        } catch (err) {
          result = err.name === 'Invariant Violation'
        }

        try {
          injectReducer('coolio', 12345)
        } catch (err) {
          result = err.name === 'Invariant Violation'
        }

        expect(result).toEqual(true)
      })
    })

    describe('injectAsyncSagas', () => {
      it('given a store, it should provide a function to inject a saga', () => {
        const injectSagas = injectAsyncSagas(store)

        injectSagas(sagas)

        const actual = store.getState().get('test')
        const expected = initialState.merge({ reduced: 'yup' })

        expect(actual.toJS()).toEqual(expected.toJS())
      })

      it('should throw if passed invalid saga', () => {
        let result = false

        const injectSagas = injectAsyncSagas(store)

        try {
          injectSagas({ testSaga })
        } catch (err) {
          result = err.name === 'Invariant Violation'
        }

        try {
          injectSagas(testSaga)
        } catch (err) {
          result = err.name === 'Invariant Violation'
        }

        expect(result).toEqual(true)
      })
    })
  })
})
