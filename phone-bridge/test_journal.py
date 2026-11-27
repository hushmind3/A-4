import os
import tempfile
import unittest

os.environ["DOMINION_PHONE_DB"] = os.path.join(tempfile.mkdtemp(), "calls.sqlite3")
import journal  # noqa: E402


class JournalTranscriptTest(unittest.TestCase):
    def setUp(self):
        journal.PATH = os.path.join(tempfile.mkdtemp(), "calls.sqlite3")

    def test_utterances_survive_call_status_lookup(self):
        call_id = "11111111-1111-4111-8111-111111111111"
        journal.create(call_id, "+821012345678", "영업시간 문의")
        self.assertTrue(journal.add_utterance(call_id, "ai", "안녕하세요. AI 비서입니다."))
        self.assertTrue(journal.add_utterance(call_id, "callee", "네, 말씀하세요."))
        row = journal.get(call_id)
        self.assertEqual([x["speaker"] for x in row["utterances"]], ["ai", "callee"])
        self.assertEqual(row["utterances"][1]["text"], "네, 말씀하세요.")

    def test_utterance_is_bounded_and_speaker_checked(self):
        call_id = "22222222-2222-4222-8222-222222222222"
        journal.create(call_id, "+821012345678", "문의")
        with self.assertRaises(ValueError):
            journal.add_utterance(call_id, "unknown", "text")
        self.assertFalse(journal.add_utterance(call_id, "ai", "x" * 801))


if __name__ == "__main__":
    unittest.main()
